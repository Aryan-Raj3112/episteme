package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.pdf.SharedPdfEmbeddedAnnotation

@Composable
internal fun SharedMobilePdfEmbeddedAnnotationLayer(
    annotations: List<SharedPdfEmbeddedAnnotation>,
    canvasSize: IntSize,
) {
    var selected by remember(annotations) { mutableStateOf<SharedPdfEmbeddedAnnotation?>(null) }
    SharedPdfEmbeddedAnnotationOverlay(
        annotations = annotations,
        canvasSize = canvasSize,
        selectedAnnotationId = selected?.id,
        onAnnotationTap = { selected = it },
    )
    selected?.let { annotation ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(annotation.author.ifBlank { "PDF comment" }) },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    SharedMobilePdfEmbeddedComment(annotation, depth = 0)
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun SharedMobilePdfEmbeddedComment(annotation: SharedPdfEmbeddedAnnotation, depth: Int) {
    Column(Modifier.padding(start = (depth * 16).dp)) {
        if (depth > 0) {
            Text(
                annotation.author.ifBlank { "Unknown author" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(annotation.contents.ifBlank { "No comment text" }, style = MaterialTheme.typography.bodyMedium)
        annotation.replies.forEach { reply ->
            Spacer(Modifier.height(12.dp))
            SharedMobilePdfEmbeddedComment(reply, depth + 1)
        }
    }
}
