package com.aryan.reader.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ui.SharedStableOutlinedTextField
import com.aryan.reader.shared.ui.readerString

@Composable
internal fun DesktopPdfPasswordDialog(
    title: String,
    isError: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember(title, isError) { mutableStateOf("") }

    LaunchedEffect(title, isError) {
        password = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(readerString("desktop_password_protected_pdf", "Password protected PDF")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (isError) {
                        readerString(
                            "desktop_pdf_password_retry_desc",
                            "That password did not open %1\$s. Enter the PDF password and try again.",
                            title
                        )
                    } else {
                        readerString("desktop_pdf_password_required_desc", "%1\$s requires a password before it can be opened.", title)
                    }
                )
                SharedStableOutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(readerString("password", "Password")) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        readerString("desktop_pdf_password_required_or_incorrect", "Password is required or incorrect."),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotEmpty(),
                onClick = { onConfirm(password) }
            ) {
                Text(readerString("action_open", "Open"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(readerString("action_cancel", "Cancel"))
            }
        }
    )
}
