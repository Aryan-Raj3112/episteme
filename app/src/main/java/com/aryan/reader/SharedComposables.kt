/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import com.aryan.reader.data.RecentFileItem
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

internal const val PRIVACY_POLICY_URL = "https://aryan-raj3112.github.io/reader-policy/privacy-policy.html"
internal const val TERMS_URL = "https://aryan-raj3112.github.io/reader-policy/terms-and-conditions.html"
internal const val LICENSES_URL = "https://aryan-raj3112.github.io/reader-policy/licenses.html"

class CustomTabUriHandler(private val context: Context) : UriHandler {
    override fun openUri(uri: String) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        try {
            customTabsIntent.launchUrl(context, uri.toUri())
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch Custom Tab, falling back to browser.")
            val browserIntent = Intent(Intent.ACTION_VIEW, uri.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "Unknown"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    return String.format(Locale.US, "%.2f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun LegalText(
    modifier: Modifier = Modifier,
    prefixText: String, // Changed from baseText
    textAlign: TextAlign = TextAlign.Center
) {
    val uriHandler = LocalUriHandler.current
    val annotatedString = buildAnnotatedString {
        append("$prefixText you agree to our ")
        pushStringAnnotation(tag = "terms", annotation = TERMS_URL)
        withStyle(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append("Terms of Service")
        }
        pop()
        append(" and acknowledge you have read our ")
        pushStringAnnotation(tag = "privacy", annotation = PRIVACY_POLICY_URL)
        withStyle(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append("Privacy Policy")
        }
        pop()
        append(".")
    }

    @Suppress("DEPRECATION")
    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodySmall.copy(
            textAlign = textAlign,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        ),
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "terms", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    uriHandler.openUri(annotation.item)
                }
            annotatedString.getStringAnnotations(tag = "privacy", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    uriHandler.openUri(annotation.item)
                }
        }
    )
}

@Composable
fun rememberFilePickerLauncher(
    onFilesSelected: (List<Uri>) -> Unit
): ManagedActivityResultLauncher<Array<String>, List<@JvmSuppressWildcards Uri>> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                Timber.d("${uris.size} file(s) selected.")
                onFilesSelected(uris)
            } else {
                Timber.d("File selection cancelled.")
            }
        }
    )
}

@Composable
fun ContextualTopAppBar(
    selectedItemCount: Int,
    onNavIconClick: () -> Unit,
    onInfoClick: (() -> Unit)? = null,
    onSelectAllClick: (() -> Unit)? = null,
    onPinClick: (() -> Unit)? = null,
    onDeleteClick: () -> Unit
) {
    CustomTopAppBar(
        title = { Text("$selectedItemCount selected") },
        navigationIcon = {
            IconButton(onClick = onNavIconClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Clear Selection")
            }
        },
        actions = {
            if (onPinClick != null) {
                IconButton(onClick = onPinClick) {
                    Icon(Icons.Filled.PushPin, contentDescription = "Pin/Unpin")
                }
            }
            if (selectedItemCount == 1 && onInfoClick != null) {
                IconButton(onClick = onInfoClick) {
                    Icon(Icons.Filled.Info, contentDescription = "Info")
                }
            }
            if (onSelectAllClick != null) {
                IconButton(onClick = onSelectAllClick) {
                    Icon(Icons.Filled.SelectAll, contentDescription = "Select All")
                }
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    )
}

@Composable
fun CustomTopAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navigationIcon()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                ProvideTextStyle(value = MaterialTheme.typography.titleLarge) {
                    title()
                }
            }
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isPermanentDelete: Boolean = false,
    containsFolderItems: Boolean = false // New parameter
) {
    val title = if (isPermanentDelete) "Delete File(s) Permanently" else "Remove from Recents"

    val text = if (isPermanentDelete) {
        if (containsFolderItems) {
            "Warning: Some selected items are synced from a local folder. Proceeding will delete the actual files from your device storage.\n\nThis action cannot be undone."
        } else {
            "Do you want to permanently delete $count selected file(s) from your device? This action cannot be undone."
        }
    } else {
        "Do you want to remove $count selected file(s) from the recent files list? It will reappear if you open it again from the library."
    }

    val confirmText = if (isPermanentDelete) "Delete" else "Remove"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text,
                color = if (containsFolderItems && isPermanentDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (containsFolderItems && isPermanentDelete) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors()
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun FileInfoDialog(item: RecentFileItem, onDismiss: () -> Unit, onUpdateName: (String?) -> Unit) {
    LocalContext.current
    @Suppress("DEPRECATION") val clipboardManager = LocalClipboardManager.current

    val originalName = item.title ?: item.displayName
    var editingName by remember { mutableStateOf(item.customName ?: originalName) }
    val hasCustomName = item.customName != null

    val formattedDate = remember(item.timestamp) {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }

    val context = LocalContext.current
    val isOpdsStream = item.uriString?.startsWith("opds-pse://") == true
    val pathText = remember(item.sourceFolderUri, item.uriString, item.displayName, context) {
        if (isOpdsStream) {
            "Source: OPDS Stream"
        } else if (item.sourceFolderUri != null && item.uriString != null) {
            try {
                val uri = item.uriString.toUri()
                val docId = if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
                    android.provider.DocumentsContract.getDocumentId(uri)
                } else if (android.provider.DocumentsContract.isTreeUri(uri)) {
                    android.provider.DocumentsContract.getTreeDocumentId(uri)
                } else {
                    Uri.decode(uri.toString())
                }

                val split = docId.split(":")
                val storageName = if (split[0].equals("primary", ignoreCase = true)) "Internal storage" else split[0]
                var relativePath = if (split.size > 1) {
                    Uri.decode(split[1]).removeSuffix("/")
                } else ""

                if (!relativePath.endsWith(item.displayName)) {
                    relativePath = if (relativePath.isEmpty()) item.displayName else "$relativePath/${item.displayName}"
                }

                val leadingSlash = if (relativePath.isNotEmpty() && !relativePath.startsWith("/")) "/" else ""

                "/$storageName$leadingSlash$relativePath"
            } catch (_: Exception) {
                val decoded = Uri.decode(item.uriString)
                if (decoded.contains("primary:")) {
                    "/Internal storage/${decoded.substringAfter("primary:").substringBeforeLast("/")}/${item.displayName}"
                } else {
                    item.displayName
                }
            }
        } else {
            "In-App Storage"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "File Information",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                androidx.compose.material3.OutlinedTextField(
                    value = editingName,
                    onValueChange = { editingName = it },
                    label = { Text("Book Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp, max = 130.dp),
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(editingName))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Name", modifier = Modifier.size(20.dp))
                        }
                    }
                )

                if (hasCustomName) {
                    Text(
                        text = "Original Name: $originalName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = {
                            editingName = originalName
                            onUpdateName(null)
                        },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Revert to Original")
                    }
                } else if (originalName != item.displayName) {
                    Text(
                        text = "File Name: ${item.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item.author?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }?.let {
                        InfoRowDetailed("Author", it)
                    }
                    InfoRowDetailed("Format", item.type.name)
                    InfoRowDetailed("Size", formatFileSize(item.fileSize))
                    InfoRowDetailed("Added", formattedDate)
                    InfoRowDetailed(
                        label = "Location",
                        value = pathText,
                        maxLines = 4,
                        isScrollable = true,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(pathText))
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.Button(onClick = {
                        val finalName = editingName.trim()
                        if (finalName != (item.customName ?: originalName)) {
                            if (finalName == originalName || finalName.isEmpty()) {
                                onUpdateName(null)
                            } else {
                                onUpdateName(finalName)
                            }
                        }
                        onDismiss()
                    }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun InfoRowDetailed(
    label: String,
    value: String,
    maxLines: Int = 1,
    isScrollable: Boolean = false, // ADD THIS
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(85.dp)
                .padding(top = 2.dp)
        )

        val scrollModifier = if (isScrollable) {
            Modifier
                .heightIn(max = 66.dp)
                .verticalScroll(rememberScrollState())
        } else Modifier

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (isScrollable) Int.MAX_VALUE else maxLines,
            overflow = if (isScrollable) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp)
                .then(scrollModifier)
        )
        if (onCopy != null) {
            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .size(24.dp)
                    .padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun CustomTopBanner(bannerMessage: BannerMessage?) {
    AnimatedVisibility(
        visible = bannerMessage != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = if (bannerMessage?.isError == true) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 8.dp
            ) {
                Text(
                    text = bannerMessage?.message ?: "",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = if (bannerMessage?.isError == true) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Suppress("KotlinConstantConditions")
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val isOss = BuildConfig.FLAVOR == "oss"

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Episteme",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isOss) "Open Source Version" else "Playstore Version",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Build ${BuildConfig.VERSION_CODE}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider()

                if (isOss) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Code,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open Source",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    InfoRow(
                        icon = Icons.Outlined.Code,
                        text = "GitHub Repository",
                        subtitle = "Browse source code, star, and fork",
                        onClick = { uriHandler.openUri("https://github.com/Aryan-Raj3112/episteme") }
                    )

                    InfoRow(
                        icon = Icons.Outlined.BugReport,
                        text = "Report an Issue",
                        subtitle = "File a bug or feature request",
                        onClick = { uriHandler.openUri("https://github.com/Aryan-Raj3112/episteme/issues") }
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Legal",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    InfoRow(
                        icon = Icons.Outlined.Policy,
                        text = "Privacy Policy",
                        subtitle = "How we handle your data",
                        onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }
                    )

                    InfoRow(
                        icon = Icons.Outlined.Gavel,
                        text = "Terms of Service",
                        subtitle = "Usage terms and conditions",
                        onClick = { uriHandler.openUri(TERMS_URL) }
                    )

                    InfoRow(
                        icon = Icons.Outlined.FileOpen,
                        text = "Licenses",
                        subtitle = "Libraries",
                        onClick = { uriHandler.openUri(LICENSES_URL) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text("Close", fontWeight = FontWeight.Medium)
            }
        }
    )
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    text: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    onSelectFileClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryButtonText: String = "Select a File",
    secondaryButtonText: String? = null,
    onSecondaryClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.FileOpen,
            contentDescription = "No files icon",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        SelectFileButton(onClick = onSelectFileClick, text = primaryButtonText)

        if (secondaryButtonText != null && onSecondaryClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onSecondaryClick) {
                Text(secondaryButtonText)
            }
        }
    }
}

@Composable
fun SelectFileButton(onClick: () -> Unit, text: String) {
    FilledTonalButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text)
    }
}

@Composable
fun ClearCloudDataConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear All Synced Data?") },
        text = { Text("Are you sure you want to permanently delete all of your book data from the cloud? This will also wipe your local library to prevent re-syncing. This action cannot be undone.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("DELETE ALL DATA")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = 1,
) {
    var scaledTextStyle by remember(text, style) { mutableStateOf(style) }
    var readyToDraw by remember(text, style) { mutableStateOf(false) }

    Text(
        text = text,
        modifier = modifier.drawWithContent {
            if (readyToDraw) {
                drawContent()
            }
        },
        style = scaledTextStyle,
        maxLines = maxLines,
        softWrap = false,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.hasVisualOverflow) {
                scaledTextStyle = scaledTextStyle.copy(
                    fontSize = scaledTextStyle.fontSize * 0.95
                )
            } else {
                readyToDraw = true
            }
        }
    )
}