package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.AppFontPreference
import com.aryan.reader.shared.CustomFontFamilyItem
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.CustomFontVariantItem
import com.aryan.reader.shared.fontFaceLabel
import com.aryan.reader.shared.fontFaceSummary
import com.aryan.reader.shared.groupByFamily
import com.aryan.reader.shared.hasVariableWeightFace

data class SharedMobileFontsStrings(
    val title: String,
    val backDescription: String,
    val selectAllDescription: String,
    val selectedCount: (Int) -> String,
    val clearSelectionDescription: String,
    val deleteDescription: String,
    val googleFonts: String,
    val importFont: String,
    val emptyTitle: String,
    val emptyMessage: String,
    val selectFile: String,
    val browseGoogleFonts: String,
    val previewText: String,
    val previewError: String,
    val variableWeight: String,
    val fileCount: (Int) -> String,
    val deleteSingleTitle: String,
    val deleteMultipleTitle: String,
    val deleteSingleBody: (String) -> String,
    val deleteMultipleBody: (Int) -> String,
    val cancelAction: String,
)

/** Exact Android Fonts screen policy and presentation, with platform font I/O injected. */
@Composable
fun SharedMobileFontsScreen(
    fonts: List<CustomFontItem>,
    appFontPreference: AppFontPreference,
    showGoogleFontsOption: Boolean,
    isLoading: Boolean,
    strings: SharedMobileFontsStrings,
    onBackClick: () -> Unit,
    onImportFonts: () -> Unit,
    onDeleteFonts: (List<String>) -> Unit,
    onAppFontPreferenceChange: (AppFontPreference) -> Unit,
    fontFamilyForPreview: (CustomFontItem) -> FontFamily?,
    googleFontsSheet: @Composable (onDismiss: () -> Unit) -> Unit,
    platformBackHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeFonts = remember(fonts) {
        fonts.filterNot { it.isDeleted }.sortedBy { it.displayName.lowercase() }
    }
    val fontFamilies = remember(activeFonts) { activeFonts.groupByFamily() }
    val fontsById = remember(activeFonts) { activeFonts.associateBy { it.id } }
    val allFontIds = remember(activeFonts) { activeFonts.mapTo(mutableSetOf()) { it.id } }
    var selectedSection by remember { mutableStateOf(SharedFontSettingsSection.READER_FONTS) }
    var selectedFontIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDeleteIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var showGoogleFontsSheet by remember { mutableStateOf(false) }
    val selectedFonts = remember(activeFonts, selectedFontIds) { activeFonts.filter { it.id in selectedFontIds } }
    val isSelectionMode = selectedSection == SharedFontSettingsSection.READER_FONTS && selectedFonts.isNotEmpty()

    LaunchedEffect(allFontIds) { selectedFontIds = selectedFontIds.intersect(allFontIds) }
    platformBackHandler(isSelectionMode) { selectedFontIds = emptySet() }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (isSelectionMode) {
                SharedMobileContextualActionBar(
                    selectedItemCount = selectedFonts.size,
                    labels = SharedMobileContextualActionLabels(
                        selectedCount = strings.selectedCount(selectedFonts.size),
                        clearSelection = strings.clearSelectionDescription,
                        info = "",
                        pin = "",
                        selectAll = strings.selectAllDescription,
                        delete = strings.deleteDescription,
                        moreOptions = "",
                        tag = "",
                        addToShelf = "",
                        save = "",
                        share = "",
                        exportAnnotations = "",
                        clear = strings.clearSelectionDescription,
                    ),
                    onNavigateBack = { selectedFontIds = emptySet() },
                    onDelete = { pendingDeleteIds = selectedFonts.map { it.id } },
                    compact = false,
                    onSelectAll = {
                        selectedFontIds = if (selectedFontIds.containsAll(allFontIds)) emptySet() else allFontIds
                    },
                    tagIcon = {},
                )
            } else {
                SharedMobileTopAppBar(
                    title = { Text(strings.title) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backDescription)
                        }
                    },
                    actions = {
                        if (selectedSection == SharedFontSettingsSection.READER_FONTS && activeFonts.isNotEmpty()) {
                            IconButton(onClick = { selectedFontIds = allFontIds }) {
                                Icon(Icons.Default.SelectAll, contentDescription = strings.selectAllDescription)
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (selectedSection == SharedFontSettingsSection.READER_FONTS && activeFonts.isNotEmpty() && !isSelectionMode) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (showGoogleFontsOption) {
                        ExtendedFloatingActionButton(
                            onClick = { showGoogleFontsSheet = true },
                            icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                            text = { Text(strings.googleFonts) },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    ExtendedFloatingActionButton(
                        onClick = onImportFonts,
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text(strings.importFont) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                SharedFontSettingsTabs(
                    selectedSection = selectedSection,
                    onSectionChange = {
                        selectedFontIds = emptySet()
                        selectedSection = it
                    },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                )
                when (selectedSection) {
                    SharedFontSettingsSection.READER_FONTS -> if (activeFonts.isEmpty()) {
                        SharedMobileEmptyLibrary(
                            title = strings.emptyTitle,
                            message = strings.emptyMessage,
                            actionLabel = strings.selectFile,
                            onAction = onImportFonts,
                            secondaryActionLabel = strings.browseGoogleFonts.takeIf { showGoogleFontsOption },
                            onSecondaryAction = { showGoogleFontsSheet = true },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(fontFamilies, key = { family -> family.variants.joinToString("|") { it.font.id } }) { family ->
                                SharedMobileFontFamilyItem(
                                    family = family,
                                    selectedFontIds = selectedFontIds,
                                    isSelectionMode = isSelectionMode,
                                    strings = strings,
                                    fontFamilyForPreview = fontFamilyForPreview,
                                    onVariantSelectionToggle = { selectedFontIds = selectedFontIds.toggle(it) },
                                    onFamilySelectionToggle = {
                                        selectedFontIds = selectedFontIds.toggleAll(family.variants.map { it.font.id })
                                    },
                                    onDeleteVariant = { pendingDeleteIds = listOf(it) },
                                )
                            }
                        }
                    }
                    SharedFontSettingsSection.APP_TEXT -> SharedAppFontSelector(
                        preference = appFontPreference,
                        customFonts = activeFonts,
                        onPreferenceChange = onAppFontPreferenceChange,
                        fontFamilyForPreview = fontFamilyForPreview,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    )
                }
            }
            if (isLoading) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)) {
                    Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
    }

    if (pendingDeleteIds.isNotEmpty()) {
        val pendingFonts = pendingDeleteIds.mapNotNull(fontsById::get)
        val single = pendingFonts.size == 1
        SharedDeleteFontsConfirmationDialog(
            title = if (single) strings.deleteSingleTitle else strings.deleteMultipleTitle,
            body = if (single) strings.deleteSingleBody(pendingFonts.first().displayName) else strings.deleteMultipleBody(pendingFonts.size),
            confirmLabel = strings.deleteDescription,
            dismissLabel = strings.cancelAction,
            onConfirm = {
                onDeleteFonts(pendingDeleteIds)
                selectedFontIds -= pendingDeleteIds.toSet()
                pendingDeleteIds = emptyList()
            },
            onDismiss = { pendingDeleteIds = emptyList() },
        )
    }
    if (showGoogleFontsSheet) googleFontsSheet { showGoogleFontsSheet = false }
}

@Composable
private fun SharedMobileFontFamilyItem(
    family: CustomFontFamilyItem,
    selectedFontIds: Set<String>,
    isSelectionMode: Boolean,
    strings: SharedMobileFontsStrings,
    fontFamilyForPreview: (CustomFontItem) -> FontFamily?,
    onVariantSelectionToggle: (String) -> Unit,
    onFamilySelectionToggle: () -> Unit,
    onDeleteVariant: (String) -> Unit,
) {
    val baseFont = remember(family) {
        family.variants.firstOrNull { it.fontFaceLabel() == "Regular" }?.font ?: family.variants.first().font
    }
    val previewFontFamily = remember(baseFont.path) { fontFamilyForPreview(baseFont) }
    val familyFontIds = remember(family) { family.variants.map { it.font.id }.toSet() }
    val faceSummary = remember(family, strings) {
        buildString {
            append(family.fontFaceSummary())
            if (family.hasVariableWeightFace()) append(" - ${strings.variableWeight}")
            append(" - ${strings.fileCount(family.variants.size)}")
        }
    }
    SharedFontFamilyCardFrame(
        isSelected = familyFontIds.any { it in selectedFontIds },
        isSelectionMode = isSelectionMode,
        onSelectionToggle = onFamilySelectionToggle,
    ) {
        SharedFontFamilyCardContent(
            familyName = family.familyName,
            faceSummary = faceSummary,
            allSelected = familyFontIds.all { it in selectedFontIds },
            isSelectionMode = isSelectionMode,
            previewText = strings.previewText,
            previewErrorText = strings.previewError,
            previewFontFamily = previewFontFamily,
            variantCount = family.variants.size,
            onFamilySelectionToggle = onFamilySelectionToggle,
        ) { index ->
            SharedMobileFontVariantRow(
                variant = family.variants[index],
                isSelected = family.variants[index].font.id in selectedFontIds,
                isSelectionMode = isSelectionMode,
                deleteDescription = strings.deleteDescription,
                onSelectionToggle = { onVariantSelectionToggle(family.variants[index].font.id) },
                onDelete = { onDeleteVariant(family.variants[index].font.id) },
            )
        }
    }
}

@Composable
private fun SharedMobileFontVariantRow(
    variant: CustomFontVariantItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    deleteDescription: String,
    onSelectionToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    SharedFontVariantRowFrame(
        isSelected = isSelected,
        isSelectionMode = isSelectionMode,
        extensionLabel = variant.font.fileExtension.uppercase(),
        deleteContentDescription = deleteDescription,
        onSelectionToggle = onSelectionToggle,
        onDelete = onDelete,
    ) {
        Column(Modifier.weight(1f)) {
            Text(variant.fontFaceLabel(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                variant.font.fileName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

private fun Set<String>.toggleAll(ids: List<String>): Set<String> {
    val set = ids.toSet()
    return if (containsAll(set)) this - set else this + set
}
