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
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.documentfile.provider.DocumentFile
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedLegalLinks
import com.aryan.reader.shared.SharedLegalProfile
import com.aryan.reader.data.BookMetadataEdit
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.shared.SharedText
import com.aryan.reader.shared.sharedLegalLinksForProfile
import com.aryan.reader.shared.ui.SharedMarkdownText
import com.aryan.reader.shared.ui.SharedBookInfoDialog
import com.aryan.reader.shared.ui.SharedMobileEmptyLibrary
import com.aryan.reader.shared.ui.SharedMobileTopBanner
import com.aryan.reader.shared.ui.SharedMobileTopAppBar
import com.aryan.reader.shared.ui.SharedMobileContextualActionBar
import com.aryan.reader.shared.ui.SharedMobileContextualActionLabels
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

internal fun legalLinksForAndroidFlavor(flavor: String = BuildConfig.FLAVOR): SharedLegalLinks {
    val profile = if (flavor == "oss") SharedLegalProfile.OSS else SharedLegalProfile.STANDARD
    return sharedLegalLinksForProfile(profile)
}

internal val PRIVACY_POLICY_URL: String get() = legalLinksForAndroidFlavor().privacyPolicyUrl
internal val TERMS_URL: String get() = legalLinksForAndroidFlavor().termsUrl
internal val LICENSES_URL: String get() = legalLinksForAndroidFlavor().licensesUrl

fun supportedFontMimeTypes(): Array<String> = arrayOf(
    "font/ttf",
    "font/otf",
    "font/woff2",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/font-woff2",
    "application/vnd.ms-opentype",
    "application/x-font-opentype"
)

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
                val fileLabel = if (uris.size == 1) "file" else "files"
                Timber.d("${uris.size} $fileLabel selected.")
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
    onSaveClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
    onExportAnnotationsClick: (() -> Unit)? = null,
    onTagClick: (() -> Unit)? = null,
    onAddToShelfClick: (() -> Unit)? = null,
    onSelectAllClick: (() -> Unit)? = null,
    onPinClick: (() -> Unit)? = null,
    onDeleteClick: () -> Unit,
    compactSelectionActions: Boolean = false,
    overflowDeleteLabelRes: Int = R.string.action_delete,
    onClearSelectionClick: (() -> Unit)? = null,
) {
    SharedMobileContextualActionBar(
        selectedItemCount = selectedItemCount,
        labels = SharedMobileContextualActionLabels(
            selectedCount = stringResource(R.string.items_selected_count, selectedItemCount),
            clearSelection = stringResource(R.string.clear_selection),
            info = stringResource(R.string.info),
            pin = stringResource(R.string.pin_unpin),
            selectAll = stringResource(R.string.select_all),
            delete = stringResource(overflowDeleteLabelRes),
            moreOptions = stringResource(R.string.content_desc_more_options),
            tag = stringResource(R.string.content_desc_tag),
            addToShelf = stringResource(R.string.desktop_add_to_shelf),
            save = stringResource(R.string.action_save_copy_to_device),
            share = stringResource(R.string.action_share),
            exportAnnotations = stringResource(R.string.action_export_annotations),
            clear = stringResource(R.string.action_clear),
        ),
        onNavigateBack = onNavIconClick,
        onDelete = onDeleteClick,
        compact = compactSelectionActions,
        onInfo = onInfoClick,
        onSave = onSaveClick,
        onShare = onShareClick,
        onExportAnnotations = onExportAnnotationsClick,
        onTag = onTagClick,
        onAddToShelf = onAddToShelfClick,
        onSelectAll = onSelectAllClick,
        onPin = onPinClick,
        onClear = onClearSelectionClick,
        tagIcon = { contentDescription ->
            Icon(
                painterResource(R.drawable.tag),
                contentDescription = contentDescription,
            )
        },
    )
}
@Composable
fun CustomTopAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    SharedMobileTopAppBar(
        modifier = modifier,
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
    )
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
fun FileInfoDialog(
    item: RecentFileItem,
    usePdfFileNameAsDisplayName: Boolean = false,
    onDismiss: () -> Unit,
    onSaveMetadata: (BookMetadataEdit) -> Unit,
    onSaveDisplayName: (String?) -> Unit,
    onRestoreMetadata: () -> Unit,
    onOpenTags: () -> Unit
) {
    val context = LocalContext.current
    var selectedCoverUri by remember(item.bookId) { mutableStateOf<Uri?>(null) }
    var selectedCoverName by remember(item.bookId) { mutableStateOf<String?>(null) }
    val coverPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            selectedCoverUri = uri
            selectedCoverName = DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment
        }
    }
    val formattedDate = remember(item.timestamp) {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    val lastModifiedDate = remember(item.lastModifiedTimestamp) {
        item.lastModifiedTimestamp
            .takeIf { it > 0L }
            ?.let { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(it)) }
    }
    val isOpdsStream = item.uriString?.startsWith("opds-pse://") == true
    val resolvedPath = remember(item.sourceFolderUri, item.uriString, item.displayName, context) {
        item.resolveDisplayPath(context, isOpdsStream)
    }
    val displayPath = when {
        isOpdsStream -> stringResource(R.string.source_opds)
        resolvedPath == "In-App Storage" -> stringResource(R.string.source_in_app)
        else -> resolvedPath.replace("Internal storage", stringResource(R.string.internal_storage))
    }
    val canEditEmbeddedMetadata = item.type == FileType.EPUB && !isOpdsStream && item.uriString != null
    val sharedBook = remember(item) {
        item.toSharedBookItem().copy(displayName = item.displayName)
    }

    SharedBookInfoDialog(
        book = sharedBook,
        canEditEmbeddedMetadata = canEditEmbeddedMetadata,
        canRenameDisplayName = !canEditEmbeddedMetadata,
        canRestoreEmbeddedMetadata = canEditEmbeddedMetadata,
        externallySelectedCoverPath = selectedCoverUri?.toString(),
        formattedAddedDate = formattedDate,
        formattedModifiedDate = lastModifiedDate,
        displayLocation = displayPath,
        displayTitle = item.cardTitle(usePdfFileNameAsDisplayName),
        initialDisplayName = item.customName ?: item.cardTitle(usePdfFileNameAsDisplayName),
        originalFileName = item.displayName,
        displayNameChanged = !item.customName.isNullOrBlank(),
        editLibraryTags = false,
        onOpenTags = onOpenTags,
        tagChipsContent = item.tags.takeIf { it.isNotEmpty() }?.let { tags ->
            { BookTagChipsRow(tags = tags, compact = false) }
        },
        embeddedEditLabel = "Edit metadata",
        coverEditorContent = if (canEditEmbeddedMetadata) {
            {
                MetadataCoverPreview(
                    item = item,
                    currentCoverPath = item.coverImagePath,
                    selectedCoverUri = selectedCoverUri,
                    selectedCoverName = selectedCoverName,
                    onChooseCover = {
                        coverPickerLauncher.launch(arrayOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"))
                    },
                    onClearCover = {
                        selectedCoverUri = null
                        selectedCoverName = null
                    },
                )
            }
        } else {
            null
        },
        onDismiss = onDismiss,
        onSave = {},
        onSaveEmbeddedMetadata = { updated ->
            onSaveMetadata(
                BookMetadataEdit(
                    title = updated.title,
                    author = updated.author,
                    seriesName = updated.seriesName,
                    seriesIndex = updated.seriesIndex,
                    description = updated.description,
                    coverImageUri = selectedCoverUri?.toString(),
                ),
            )
        },
        onSaveDisplayName = onSaveDisplayName,
        onRestore = { onRestoreMetadata() },
    )
}

@Composable
private fun MetadataCoverPreview(
    item: RecentFileItem,
    currentCoverPath: String?,
    selectedCoverUri: Uri?,
    selectedCoverName: String?,
    onChooseCover: () -> Unit,
    onClearCover: () -> Unit
) {
    val context = LocalContext.current
    val currentCoverFile = remember(currentCoverPath) {
        currentCoverPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }
    }
    val previewModel = selectedCoverUri ?: currentCoverFile
    val previewRequest = remember(previewModel, context) {
        previewModel?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .build()
        }
    }

    Text(stringResource(R.string.label_cover), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 104.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            ThemedBookCover(
                item = item,
                modifier = Modifier.fillMaxSize(),
                contentDescription = null
            )
            if (previewRequest != null) {
                AsyncImage(
                    model = previewRequest,
                    contentDescription = stringResource(R.string.label_cover),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                selectedCoverName ?: if (currentCoverFile != null) {
                    stringResource(R.string.label_current_cover)
                } else {
                    stringResource(R.string.label_current_cover_generated)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onChooseCover) {
                    Text(stringResource(R.string.action_change_cover))
                }
                if (selectedCoverName != null) {
                    TextButton(onClick = onClearCover) {
                        Text(stringResource(R.string.action_clear_selection))
                    }
                }
            }
        }
    }
}

private fun RecentFileItem.resolveDisplayPath(context: Context, isOpdsStream: Boolean): String {
    return if (isOpdsStream) {
        "Source: OPDS Stream"
    } else if (sourceFolderUri != null && uriString != null) {
        try {
            val uri = uriString.toUri()
            val docId = if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
                android.provider.DocumentsContract.getDocumentId(uri)
            } else if (android.provider.DocumentsContract.isTreeUri(uri)) {
                android.provider.DocumentsContract.getTreeDocumentId(uri)
            } else {
                Uri.decode(uri.toString())
            }

            val split = docId.split(":")
            val storageName = if (split[0].equals("primary", ignoreCase = true)) "Internal storage" else split[0]
            var relativePath = if (split.size > 1) Uri.decode(split[1]).removeSuffix("/") else ""

            if (!relativePath.endsWith(displayName)) {
                relativePath = if (relativePath.isEmpty()) displayName else "$relativePath/$displayName"
            }

            val leadingSlash = if (relativePath.isNotEmpty() && !relativePath.startsWith("/")) "/" else ""
            "/$storageName$leadingSlash$relativePath"
        } catch (_: Exception) {
            val decoded = Uri.decode(uriString)
            if (decoded.contains("primary:")) {
                "/Internal storage/${decoded.substringAfter("primary:").substringBeforeLast("/")}/$displayName"
            } else {
                displayName
            }
        }
    } else {
        "In-App Storage"
    }
}

@Composable
fun CustomTopBanner(bannerMessage: BannerMessage?) {
    val context = LocalContext.current
    val bannerText = bannerMessage?.localizedMessage(context).orEmpty()
    SharedMobileTopBanner(
        text = bannerText,
        visible = bannerMessage != null,
        isError = bannerMessage?.isError == true,
    )
}

private fun BannerMessage.localizedMessage(context: Context): String {
    return text?.resolveAndroidText(context) ?: message
}

private fun SharedText.resolveAndroidText(context: Context): String {
    val resources = context.resources
    val packageName = context.packageName
    val formatArgs = args.toTypedArray()
    val quantityValue = quantity
    val resolved = if (quantityValue == null) {
        val id = resources.getIdentifier(name, "string", packageName)
        if (id == 0) null else runCatching { resources.getString(id, *formatArgs) }.getOrNull()
    } else {
        val id = resources.getIdentifier(name, "plurals", packageName)
        if (id == 0) null else runCatching { resources.getQuantityString(id, quantityValue, *formatArgs) }.getOrNull()
    }
    return resolved ?: fallbackMessage()
}

@Suppress("KotlinConstantConditions")
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val isOss = BuildConfig.FLAVOR == "oss"
    com.aryan.reader.shared.ui.SharedAndroidAboutDialog(
        strings = com.aryan.reader.shared.ui.SharedAndroidAboutStrings(
            appName = stringResource(R.string.about_app_name),
            flavorLabel = if (isOss) stringResource(R.string.about_oss_version) else stringResource(R.string.about_play_version),
            versionLabel = stringResource(R.string.about_version_name, BuildConfig.VERSION_NAME),
            buildLabel = stringResource(R.string.about_build_code, BuildConfig.VERSION_CODE.toString()),
            githubTitle = stringResource(R.string.about_github),
            githubDescription = stringResource(R.string.about_github_desc),
            privacyTitle = stringResource(R.string.legal_privacy_policy),
            privacyDescription = stringResource(R.string.about_privacy_desc),
            termsTitle = stringResource(R.string.legal_terms_of_service),
            termsDescription = stringResource(R.string.about_terms_desc),
            licensesTitle = stringResource(R.string.legal_licenses),
            licensesDescription = stringResource(R.string.about_licenses_desc),
            closeAction = stringResource(R.string.action_close),
        ),
        showGitHub = isOss,
        onDismiss = onDismiss,
        onGitHubClick = { uriHandler.openUri("https://github.com/Aryan-Raj3112/episteme") },
        onPrivacyClick = { uriHandler.openUri(PRIVACY_POLICY_URL) },
        onTermsClick = { uriHandler.openUri(TERMS_URL) },
        onLicensesClick = { uriHandler.openUri(LICENSES_URL) },
        githubIcon = {
            Icon(
                painterResource(R.drawable.github),
                contentDescription = stringResource(R.string.about_github),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    )
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
    SharedMobileEmptyLibrary(
        title = title,
        message = message,
        actionLabel = primaryButtonText,
        onAction = onSelectFileClick,
        secondaryActionLabel = secondaryButtonText.takeIf { onSecondaryClick != null },
        onSecondaryAction = { onSecondaryClick?.invoke() },
        modifier = modifier,
    )
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
fun FileTypeBadge(
    type: FileType,
    modifier: Modifier = Modifier,
    overlay: Boolean = false,
    compact: Boolean = false
) {
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
            text = if (type == FileType.UNKNOWN) "FILE" else type.name.uppercase(),
            style = if (compact) {
                MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.sp)
            } else {
                MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp)
            },
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 10.dp,
                vertical = if (compact) 3.dp else 4.dp
            )
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

fun RecentFileItem.cardTitle(usePdfFileNameAsDisplayName: Boolean = false): String {
    customName?.takeIf { it.isNotBlank() }?.let { return it }
    if (usePdfFileNameAsDisplayName && type == FileType.PDF) {
        return displayName
    }
    return title?.takeIf { it.isNotBlank() } ?: displayName
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

fun RecentFileItem.canExportOriginalFile(): Boolean {
    return uriString != null && !isOpdsStream()
}

fun RecentFileItem.suggestedOriginalFileName(): String {
    val fallbackExtension = SharedFileCapabilities.primaryExtensionFor(type)
    val baseName = displayName
        .takeIf { it.isNotBlank() }
        ?: title?.takeIf { it.isNotBlank() }
        ?: "book"
    val sanitized = baseName
        .replace(Regex("""[\\/:*?"<>|]+"""), "_")
        .trim()
        .take(120)
        .ifBlank { "book" }
    return if (
        fallbackExtension != null &&
        !sanitized.endsWith(".$fallbackExtension", ignoreCase = true)
    ) {
        "$sanitized.$fallbackExtension"
    } else {
        sanitized
    }
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
    onDeleteTag: (TagEntity) -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var tagPendingDeletion by remember { mutableStateOf<TagEntity?>(null) }

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
                        Text(
                            tag.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { tagPendingDeletion = tag }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.menu_delete_tag),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    tagPendingDeletion?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagPendingDeletion = null },
            title = { Text(stringResource(R.string.menu_delete_tag)) },
            text = { Text(stringResource(R.string.dialog_delete_tag_desc, tag.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTag(tag)
                    tagPendingDeletion = null
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { tagPendingDeletion = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
