package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

data class SharedMobileSingleChoiceOption<T>(val value: T, val label: String)

@Composable
fun <T> SharedMobileSingleChoiceDialog(
    title: String,
    description: String,
    cancelLabel: String,
    options: List<SharedMobileSingleChoiceOption<T>>,
    selectedValue: T,
    onDismiss: () -> Unit,
    onSelected: (T) -> Unit,
    firstRunMessage: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                Text(description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                if (firstRunMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(bottom = 16.dp),
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                firstRunMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                }
                options.forEach { option ->
                    val selected = option.value == selectedValue
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).selectable(
                            selected = selected,
                            onClick = { onSelected(option.value) },
                            role = Role.RadioButton,
                        ).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(option.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(cancelLabel) } },
    )
}

@Composable
fun SharedMobileInfoConfirmationDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    icon: @Composable (() -> Unit)? = {
        Icon(Icons.Default.Info, contentDescription = null)
    },
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

@Composable
fun SharedMobileExternalLinkDialog(
    title: String,
    warning: String,
    visitLabel: String,
    copyLabel: String,
    cancelLabel: String,
    onVisit: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(warning) },
        confirmButton = { TextButton(onClick = onVisit) { Text(visitLabel) } },
        dismissButton = {
            Row {
                TextButton(onClick = onCopy) { Text(copyLabel) }
                TextButton(onClick = onDismiss) { Text(cancelLabel) }
            }
        },
    )
}

@Composable
fun SharedMobileDocumentFormatDialog(
    title: String,
    description: String,
    annotatedLabel: String,
    originalLabel: String,
    cancelLabel: String,
    onAnnotated: () -> Unit,
    onOriginal: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(description) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onAnnotated) { Text(annotatedLabel) }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOriginal) { Text(originalLabel) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text(cancelLabel) }
            }
        },
    )
}
