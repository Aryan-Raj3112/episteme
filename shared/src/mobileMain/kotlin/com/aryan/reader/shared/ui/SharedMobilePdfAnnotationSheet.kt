package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.readerExternalLookupActionsAvailable
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.generated.resources.Res
import com.aryan.reader.shared.generated.resources.copy
import com.aryan.reader.shared.generated.resources.font_background
import com.aryan.reader.shared.generated.resources.format_underlined
import com.aryan.reader.shared.generated.resources.format_underlined_squiggle
import com.aryan.reader.shared.generated.resources.strikethrough
import com.aryan.reader.shared.generated.resources.translate
import com.aryan.reader.shared.pdf.DEFAULT_SHARED_PDF_COMMENT_AUTHOR
import com.aryan.reader.shared.pdf.SharedPdfAndroidHighlightColors
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationComment
import com.aryan.reader.shared.pdf.pdfCommentChildren
import com.aryan.reader.shared.pdf.visiblePdfAnnotationComments
import com.aryan.reader.shared.pdf.withoutPdfCommentThread
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private enum class SharedMobilePdfAnnotationSection { NOTE, COMMENTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedMobilePdfAnnotationBottomSheet(
    annotation: SharedPdfAnnotation,
    onUpdate: (SharedPdfAnnotation) -> Unit,
    onDelete: () -> Unit,
    onReadAloud: () -> Unit,
    onClipboardError: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var noteText by remember(annotation.id) { mutableStateOf(annotation.note.orEmpty()) }
    var comments by remember(annotation.id) { mutableStateOf(annotation.comments) }
    var section by remember(annotation.id) { mutableStateOf(SharedMobilePdfAnnotationSection.NOTE) }
    var commentText by remember(annotation.id) { mutableStateOf("") }
    var commentAuthor by remember(annotation.id) { mutableStateOf(DEFAULT_SHARED_PDF_COMMENT_AUTHOR) }
    var replyTargetId by remember(annotation.id) { mutableStateOf<String?>(null) }
    var editingCommentId by remember(annotation.id) { mutableStateOf<String?>(null) }
    val copiedTextLabel = readerString("clip_label_copied_text", "Copied Text")
    val clipboardErrorMessage = readerString("error_copy_to_clipboard", "Could not copy to clipboard")

    fun updateComments(next: List<SharedPdfAnnotationComment>) {
        comments = next
        onUpdate(annotation.copy(note = noteText.trim().ifBlank { null }, comments = next))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp)
        ) {
            SharedMobilePdfHighlightStyleRow(annotation.highlightStyle) { style ->
                onUpdate(annotation.copy(highlightStyle = style, note = noteText.ifBlank { null }, comments = comments))
            }
            SharedMobilePdfHighlightColorRow(annotation.colorArgb) { color ->
                onUpdate(annotation.copy(colorArgb = color, note = noteText.ifBlank { null }, comments = comments))
            }
            val displayColor = Color(annotation.colorArgb)
            Surface(
                color = displayColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, displayColor.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.heightIn(min = 64.dp)) {
                    Box(Modifier.width(6.dp).fillMaxHeight().background(displayColor))
                    Text(
                        text = "\"${annotation.text.ifBlank { "Highlighted section" }}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SharedMobilePdfAnnotationTool(icon = Res.drawable.copy, label = "Copy") {
                    val result = writeSharedClipboard(copiedTextLabel, annotation.text)
                    if (!result.success) onClipboardError?.invoke(clipboardErrorMessage)
                }
                SharedMobilePdfAnnotationTool(imageVector = Icons.AutoMirrored.Filled.VolumeUp, label = "Read aloud", onClick = onReadAloud)
                if (readerExternalLookupActionsAvailable(annotation.text.length)) {
                    SharedMobilePdfAnnotationTool(imageVector = Icons.Default.Book, label = "Define") {
                        openSharedMobileEpubLookup(ReaderExternalLookupAction.DICTIONARY, annotation.text)
                    }
                    SharedMobilePdfAnnotationTool(icon = Res.drawable.translate, label = "Translate") {
                        openSharedMobileEpubLookup(ReaderExternalLookupAction.TRANSLATE, annotation.text)
                    }
                    SharedMobilePdfAnnotationTool(imageVector = Icons.Default.Search, label = "Search") {
                        openSharedMobileEpubLookup(ReaderExternalLookupAction.SEARCH, annotation.text)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SharedMobilePdfAnnotationTabs(section, comments.count { it.contents.isNotBlank() }) { section = it }
            Spacer(Modifier.height(12.dp))
            if (section == SharedMobilePdfAnnotationSection.NOTE) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Add a note") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp)
                )
            } else {
                SharedMobilePdfCommentsEditor(
                    comments = comments,
                    commentText = commentText,
                    commentAuthor = commentAuthor,
                    replyTargetId = replyTargetId,
                    editingCommentId = editingCommentId,
                    onTextChange = { commentText = it },
                    onAuthorChange = { commentAuthor = it },
                    onReply = { replyTargetId = it.id; editingCommentId = null; commentText = "" },
                    onEdit = { editingCommentId = it.id; replyTargetId = null; commentText = it.contents; commentAuthor = it.author.ifBlank { DEFAULT_SHARED_PDF_COMMENT_AUTHOR } },
                    onCancel = { replyTargetId = null; editingCommentId = null; commentText = "" },
                    onDelete = { target -> updateComments(comments.withoutPdfCommentThread(target.id)) },
                    onSubmit = {
                        val contents = commentText.trim()
                        if (contents.isNotBlank()) {
                            val author = commentAuthor.trim().ifBlank { DEFAULT_SHARED_PDF_COMMENT_AUTHOR }
                            val now = currentTimestamp()
                            val next = if (editingCommentId != null) {
                                comments.map { if (it.id == editingCommentId) it.copy(author = author, contents = contents, modifiedAt = now) else it }
                            } else {
                                comments + SharedPdfAnnotationComment(
                                    id = "pdf_comment_${now}_${comments.size}", parentId = replyTargetId,
                                    author = author, contents = contents, createdAt = now, modifiedAt = now
                                )
                            }
                            updateComments(next)
                            commentText = ""; replyTargetId = null; editingCommentId = null
                        }
                    }
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Delete")
                }
                Button(onClick = {
                    onUpdate(annotation.copy(note = noteText.trim().ifBlank { null }, comments = comments))
                    onDismiss()
                }) { Text("Done") }
            }
        }
    }
}

@Composable
private fun SharedMobilePdfHighlightStyleRow(selected: HighlightStyle, onSelect: (HighlightStyle) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HighlightStyle.entries.forEach { style ->
            val resource = when (style) {
                HighlightStyle.BACKGROUND -> Res.drawable.font_background
                HighlightStyle.UNDERLINE -> Res.drawable.format_underlined
                HighlightStyle.WAVY_UNDERLINE -> Res.drawable.format_underlined_squiggle
                HighlightStyle.STRIKETHROUGH -> Res.drawable.strikethrough
            }
            Surface(
                color = if (style == selected) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else Color.Transparent,
                contentColor = if (style == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (style == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(width = 38.dp, height = 30.dp).clickable { onSelect(style) }
            ) { Box(contentAlignment = Alignment.Center) { Icon(painterResource(resource), style.id, Modifier.size(18.dp)) } }
        }
    }
}

@Composable
private fun SharedMobilePdfHighlightColorRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SharedPdfAndroidHighlightColors.palette.forEach { color ->
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color(color))
                    .then(if (color == selected) Modifier.padding(3.dp).clip(CircleShape).background(Color(color)) else Modifier)
                    .clickable { onSelect(color) }
            )
        }
    }
}

@Composable
private fun SharedMobilePdfAnnotationTabs(selected: SharedMobilePdfAnnotationSection, count: Int, onSelect: (SharedMobilePdfAnnotationSection) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f), RoundedCornerShape(8.dp)).padding(4.dp)) {
        listOf(SharedMobilePdfAnnotationSection.NOTE to "Note", SharedMobilePdfAnnotationSection.COMMENTS to "Comments ($count)").forEach { (section, label) ->
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (selected == section) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(section) },
                contentAlignment = Alignment.Center
            ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected == section) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
private fun SharedMobilePdfCommentsEditor(
    comments: List<SharedPdfAnnotationComment>, commentText: String, commentAuthor: String,
    replyTargetId: String?, editingCommentId: String?, onTextChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit, onReply: (SharedPdfAnnotationComment) -> Unit,
    onEdit: (SharedPdfAnnotationComment) -> Unit, onCancel: () -> Unit,
    onDelete: (SharedPdfAnnotationComment) -> Unit, onSubmit: () -> Unit
) {
    val visible = comments.visiblePdfAnnotationComments()
    Column {
        Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
            SharedMobilePdfCommentThread(visible, null, 0, emptySet(), onReply, onEdit, onDelete)
        }
        if (replyTargetId != null || editingCommentId != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (editingCommentId != null) "Editing comment" else "Replying to comment", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
        OutlinedTextField(commentAuthor, onAuthorChange, label = { Text("Author") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(commentText, onTextChange, placeholder = { Text("Add a comment") }, modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp), maxLines = 4, shape = RoundedCornerShape(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onSubmit, enabled = commentText.isNotBlank()) { Text(if (editingCommentId != null) "Save comment" else "Add comment") }
        }
    }
}

@Composable
private fun SharedMobilePdfCommentThread(
    comments: List<SharedPdfAnnotationComment>, parentId: String?, depth: Int, visited: Set<String>,
    onReply: (SharedPdfAnnotationComment) -> Unit, onEdit: (SharedPdfAnnotationComment) -> Unit,
    onDelete: (SharedPdfAnnotationComment) -> Unit
) {
    comments.pdfCommentChildren(parentId).forEach { comment ->
        if (comment.id !in visited) {
            Row(Modifier.fillMaxWidth().padding(start = (depth * 16).dp, top = 6.dp, bottom = 6.dp)) {
                if (depth > 0) { Box(Modifier.width(2.dp).height(60.dp).background(MaterialTheme.colorScheme.outlineVariant)); Spacer(Modifier.width(12.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(comment.author.ifBlank { DEFAULT_SHARED_PDF_COMMENT_AUTHOR }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(comment.contents, style = MaterialTheme.typography.bodyMedium)
                    Row { TextButton({ onReply(comment) }) { Text("Reply") }; TextButton({ onEdit(comment) }) { Text("Edit") }; TextButton({ onDelete(comment) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
                }
            }
            SharedMobilePdfCommentThread(comments, comment.id, depth + 1, visited + comment.id, onReply, onEdit, onDelete)
        }
    }
}

@Composable
private fun SharedMobilePdfAnnotationTool(icon: DrawableResource? = null, imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null, label: String, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) Icon(painterResource(icon), label, Modifier.size(22.dp)) else if (imageVector != null) Icon(imageVector, label, Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
