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
// SharedComposables.kt
package com.aryan.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.state.ToggleableState
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.aryan.reader.data.TagEntity
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlin.math.roundToInt

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
    prefixText: String,
    textAlign: TextAlign = TextAlign.Center
) {
    val uriHandler = LocalUriHandler.current

    val fullAgreementText = stringResource(R.string.legal_agreement_full, prefixText, stringResource(R.string.legal_terms_of_service), stringResource(R.string.legal_privacy_policy))
    val termsText = stringResource(R.string.legal_terms_of_service)
    val privacyText = stringResource(R.string.legal_privacy_policy)

    val annotatedString = buildAnnotatedString {
        append(fullAgreementText)

        val termsStartIndex = fullAgreementText.indexOf(termsText)
        if (termsStartIndex >= 0) {
            addStringAnnotation(tag = "terms", annotation = TERMS_URL, start = termsStartIndex, end = termsStartIndex + termsText.length)
            addStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline), start = termsStartIndex, end = termsStartIndex + termsText.length)
        }

        val privacyStartIndex = fullAgreementText.indexOf(privacyText)
        if (privacyStartIndex >= 0) {
            addStringAnnotation(tag = "privacy", annotation = PRIVACY_POLICY_URL, start = privacyStartIndex, end = privacyStartIndex + privacyText.length)
            addStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline), start = privacyStartIndex, end = privacyStartIndex + privacyText.length)
        }
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
    onTagClick: (() -> Unit)? = null,
    onSelectAllClick: (() -> Unit)? = null,
    onPinClick: (() -> Unit)? = null,
    onDeleteClick: () -> Unit
) {
    CustomTopAppBar(
        title = { Text(stringResource(R.string.items_selected_count, selectedItemCount)) },
        navigationIcon = {
            IconButton(onClick = onNavIconClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.clear_selection))
            }
        },
        actions = {
            if (onTagClick != null) {
                IconButton(onClick = onTagClick) {
                    Icon(painterResource(id = R.drawable.tag), contentDescription = stringResource(R.string.content_desc_tag))
                }
            }
            if (onPinClick != null) {
                IconButton(onClick = onPinClick) {
                    Icon(Icons.Filled.PushPin, contentDescription = stringResource(R.string.pin_unpin))
                }
            }
            if (selectedItemCount == 1 && onInfoClick != null) {
                IconButton(onClick = onInfoClick) {
                    Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.info))
                }
            }
            if (onSelectAllClick != null) {
                IconButton(onClick = onSelectAllClick) {
                    Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.select_all))
                }
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
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
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
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
    containsFolderItems: Boolean = false
) {
    val title = if (isPermanentDelete) pluralStringResource(R.plurals.dialog_delete_permanently, count) else stringResource(R.string.dialog_remove_from_recents)

    val text = if (isPermanentDelete) {
        if (containsFolderItems) {
            stringResource(R.string.dialog_warning_folder_sync_delete)
        } else {
            pluralStringResource(R.plurals.dialog_permanently_delete_desc, count, count)
        }
    } else {
        pluralStringResource(R.plurals.dialog_remove_recents_desc, count, count)
    }

    val confirmText = if (isPermanentDelete) stringResource(R.string.action_delete) else stringResource(R.string.action_remove)

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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun FileInfoDialog(item: RecentFileItem, onDismiss: () -> Unit, onUpdateName: (String?) -> Unit, onOpenTags: () -> Unit) {
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
                    stringResource(R.string.file_information),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                androidx.compose.material3.OutlinedTextField(
                    value = editingName,
                    onValueChange = { editingName = it },
                    label = { Text(stringResource(R.string.book_name)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp, max = 130.dp),
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(editingName))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_name), modifier = Modifier.size(20.dp))
                        }
                    }
                )

                if (hasCustomName) {
                    Text(
                        text = stringResource(R.string.original_name, originalName),
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
                        Text(stringResource(R.string.revert_to_original))
                    }
                } else if (originalName != item.displayName) {
                    Text(
                        text = stringResource(R.string.file_name, item.displayName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item.author?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }?.let {
                        InfoRowDetailed(stringResource(R.string.author), it)
                    }
                    item.seriesName?.takeIf { it.isNotBlank() }?.let { series ->
                        val seriesText = if (item.seriesIndex != null && item.seriesIndex > 0) {
                            "$series #${item.seriesIndex.toInt()}"
                        } else {
                            series
                        }
                        InfoRowDetailed("Series", seriesText)
                    }
                    InfoRowDetailed(stringResource(R.string.format), item.type.name)
                    InfoRowDetailed(stringResource(R.string.size), formatFileSize(item.fileSize))
                    InfoRowDetailed(stringResource(R.string.added), formattedDate)

                    val pathTextFinal = if (isOpdsStream) {
                        stringResource(R.string.source_opds)
                    } else if (pathText == "In-App Storage") {
                        stringResource(R.string.source_in_app)
                    } else {
                        pathText.replace("Internal storage", stringResource(R.string.internal_storage))
                    }

                    InfoRowDetailed(
                        label = stringResource(R.string.location),
                        value = pathTextFinal,
                        maxLines = 4,
                        isScrollable = true,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(pathTextFinal))
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.section_tags), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onOpenTags) { Text(stringResource(R.string.action_add_edit)) }
                }

                if (item.tags.isNotEmpty()) {
                    BookTagChipsRow(tags = item.tags, compact = false)
                } else {
                    Text(stringResource(R.string.msg_no_tags_assigned), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
                    }) { Text(stringResource(R.string.action_save)) }
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
    isScrollable: Boolean = false,
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
                    contentDescription = stringResource(R.string.content_desc_copy_value, label),
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.about_app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isOss) stringResource(R.string.about_oss_version) else stringResource(R.string.about_play_version),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.about_version_name, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.about_build_code, BuildConfig.VERSION_CODE.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (isOss) {
                    AboutInfoRow(
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.github),
                                contentDescription = stringResource(R.string.about_github),
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        text = stringResource(R.string.about_github),
                        subtitle = stringResource(R.string.about_github_desc),
                        onClick = { uriHandler.openUri("https://github.com/Aryan-Raj3112/episteme") }
                    )
                } else {
                    AboutInfoRow(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Policy,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        text = stringResource(R.string.legal_privacy_policy),
                        subtitle = stringResource(R.string.about_privacy_desc),
                        onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    AboutInfoRow(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Gavel,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        text = stringResource(R.string.legal_terms_of_service),
                        subtitle = stringResource(R.string.about_terms_desc),
                        onClick = { uriHandler.openUri(TERMS_URL) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    AboutInfoRow(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.FileOpen,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        text = stringResource(R.string.legal_licenses),
                        subtitle = stringResource(R.string.about_licenses_desc),
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
                Text(stringResource(R.string.action_close), fontWeight = FontWeight.Medium)
            }
        }
    )
}

@Composable
private fun AboutInfoRow(
    icon: @Composable () -> Unit,
    text: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
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
    primaryButtonText: String = stringResource(R.string.empty_select_file),
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
            contentDescription = stringResource(R.string.content_desc_no_files_icon),
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
        title = { Text(stringResource(R.string.clear_cloud_data_title)) },
        text = { Text(stringResource(R.string.clear_cloud_data_desc)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.delete_all_data)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
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

@Composable
fun FileTypeBadge(type: FileType, modifier: Modifier = Modifier, overlay: Boolean = false) {
    val containerColor = if (overlay) Color.Black.copy(alpha = 0.6f) else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (overlay) Color.White else MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
        border = if (overlay) BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)) else null
    ) {
        Text(
            text = type.name.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun TagEntity.displayColor(): Color = Color(color ?: 0xFF64B5F6.toInt())

@Composable
fun BookTagChipsRow(
    tags: List<TagEntity>,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    if (tags.isEmpty()) return

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tags.forEach { tag ->
            val tagColor = tag.displayColor()
            Surface(
                shape = RoundedCornerShape(50),
                color = tagColor.copy(alpha = 0.14f),
                contentColor = tagColor
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = if (compact) 8.dp else 10.dp,
                        vertical = if (compact) 4.dp else 6.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (compact) 6.dp else 8.dp)
                            .background(tagColor, androidx.compose.foundation.shape.CircleShape)
                    )
                    Text(
                        text = tag.name,
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private const val UNKNOWN_AUTHOR_LABEL = "No author listed"

fun RecentFileItem.cardTitle(): String {
    return customName ?: title?.takeIf { it.isNotBlank() } ?: displayName
}

fun RecentFileItem.cardAuthor(): String {
    return author
        ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
        ?: UNKNOWN_AUTHOR_LABEL
}

fun RecentFileItem.progressPercentValue(): Int {
    return (progressPercentage ?: 0f).coerceIn(0f, 100f).roundToInt()
}

fun RecentFileItem.progressFraction(): Float {
    return progressPercentValue() / 100f
}

fun RecentFileItem.isOpdsStream(): Boolean {
    return uriString?.startsWith("opds-pse://") == true
}

@Composable
private fun statusBadgeColors(overlay: Boolean): Pair<Color, Color> {
    val container = if (overlay) {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = if (overlay) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    return container to content
}

@Composable
fun StatusIconBadge(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    overlay: Boolean = false,
) {
    val (containerColor, contentColor) = statusBadgeColors(overlay)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (overlay) 0.dp else 2.dp,
        shadowElevation = if (overlay) 0.dp else 1.dp,
        border = if (overlay) BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)) else null
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.padding(6.dp).size(14.dp)
        )
    }
}

@Composable
fun FileStatusBadges(
    item: RecentFileItem,
    isPinned: Boolean,
    modifier: Modifier = Modifier,
    overlay: Boolean = false,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.sourceFolderUri != null) {
            StatusIconBadge(
                icon = Icons.Default.Folder,
                contentDescription = stringResource(R.string.local_folder),
                overlay = overlay
            )
        }
        if (item.isOpdsStream()) {
            StatusIconBadge(
                icon = Icons.Default.Cloud,
                contentDescription = stringResource(R.string.opds_stream),
                overlay = overlay
            )
        }
        if (isPinned) {
            StatusIconBadge(
                icon = Icons.Default.PushPin,
                contentDescription = stringResource(R.string.pinned),
                overlay = overlay
            )
        }
    }
}

@Composable
fun ReadingProgressSection(
    progressPercentage: Float?,
    modifier: Modifier = Modifier,
    label: String? = null,
    compact: Boolean = false,
) {
    val percent = (progressPercentage ?: 0f).coerceIn(0f, 100f).roundToInt()
    val progress = percent / 100f

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (label != null) {
                Text(
                    text = label,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    text = "$percent%",
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(if (compact) 6.dp else 8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 5.dp else 6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagSelectionBottomSheet(
    allTags: List<TagEntity>,
    selectedBookIds: Set<String>,
    booksWithTags: List<RecentFileItem>,
    onCreateAndAssign: (String) -> Unit,
    onToggleTag: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val filteredTags = remember(allTags, searchQuery) {
        if (searchQuery.isBlank()) allTags else allTags.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val exactMatch = allTags.any { it.name.equals(searchQuery.trim(), ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(max = 500.dp)) {
            Text(stringResource(R.string.title_apply_tags), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))

            androidx.compose.material3.OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.placeholder_search_create_tag)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                if (searchQuery.isNotBlank() && !exactMatch) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onCreateAndAssign(searchQuery)
                                searchQuery = ""
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(stringResource(R.string.action_create_tag, searchQuery.trim()), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                items(filteredTags, key = { it.id }) { tag ->
                    var checkedCount = 0
                    selectedBookIds.forEach { bookId ->
                        val book = booksWithTags.find { it.bookId == bookId }
                        if (book?.tags?.any { it.id == tag.id } == true) checkedCount++
                    }

                    val state = when (checkedCount) {
                        0 -> ToggleableState.Off
                        selectedBookIds.size -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val assign = state != ToggleableState.On
                            onToggleTag(tag.id, assign)
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TriStateCheckbox(state = state, onClick = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color(tag.color ?: 0xFF64B5F6.toInt()).copy(alpha = 0.2f), modifier = Modifier.size(24.dp)) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(painterResource(id = R.drawable.tag), contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(tag.color ?: 0xFF64B5F6.toInt()))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(tag.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
