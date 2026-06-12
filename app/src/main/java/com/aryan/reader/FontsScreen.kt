// FontsScreen.kt
@file:Suppress("KotlinConstantConditions")

package com.aryan.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.CustomFontFamilyItem
import com.aryan.reader.shared.CustomFontVariantItem
import com.aryan.reader.shared.fontFaceLabel
import com.aryan.reader.shared.fontFaceSummary
import com.aryan.reader.shared.groupByFamily
import com.aryan.reader.shared.hasVariableWeightFace
import com.aryan.reader.shared.ui.SharedAppFontSelector
import com.aryan.reader.shared.ui.SharedFontSettingsSection
import com.aryan.reader.shared.ui.SharedFontSettingsTabs
import com.aryan.reader.data.CustomFontEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontsScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val fonts: List<CustomFontEntity> by viewModel.customFonts.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val showGoogleFontsOption = !(BuildConfig.FLAVOR == "oss" && BuildConfig.IS_OFFLINE)

    var fontsPendingDelete by remember { mutableStateOf<List<CustomFontEntity>>(emptyList()) }
    var showGoogleFontsSheet by remember { mutableStateOf(false) }
    var selectedSection by remember { mutableStateOf(SharedFontSettingsSection.READER_FONTS) }
    var selectedFontIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val pickFontLauncher = rememberFilePickerLauncher(viewModel::importFonts)
    val fontMimeTypes = remember { supportedFontMimeTypes() }
    val allFontIds = remember(fonts) { fonts.mapTo(mutableSetOf()) { it.id } }
    val selectedFonts = remember(fonts, selectedFontIds) {
        fonts.filter { it.id in selectedFontIds }
    }
    val fontEntitiesById = remember(fonts) { fonts.associateBy { it.id } }
    val isFontSelectionMode = selectedSection == SharedFontSettingsSection.READER_FONTS && selectedFonts.isNotEmpty()

    LaunchedEffect(fonts) {
        selectedFontIds = selectedFontIds.intersect(allFontIds)
    }

    BackHandler(enabled = isFontSelectionMode) {
        selectedFontIds = emptySet()
    }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            if (isFontSelectionMode) {
                ContextualTopAppBar(
                    selectedItemCount = selectedFonts.size,
                    onNavIconClick = { selectedFontIds = emptySet() },
                    onSelectAllClick = {
                        selectedFontIds = if (selectedFontIds.containsAll(allFontIds)) {
                            emptySet()
                        } else {
                            allFontIds
                        }
                    },
                    onDeleteClick = {
                        if (selectedFonts.isNotEmpty()) {
                            fontsPendingDelete = selectedFonts
                        }
                    }
                )
            } else {
                CustomTopAppBar(
                    title = { Text(stringResource(R.string.custom_fonts)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        if (selectedSection == SharedFontSettingsSection.READER_FONTS && fonts.isNotEmpty()) {
                            IconButton(onClick = { selectedFontIds = allFontIds }) {
                                Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.select_all))
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (selectedSection == SharedFontSettingsSection.READER_FONTS && fonts.isNotEmpty() && !isFontSelectionMode) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (showGoogleFontsOption) {
                        ExtendedFloatingActionButton(
                            onClick = { showGoogleFontsSheet = true },
                            icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                            text = { Text(stringResource(R.string.google_fonts)) },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    ExtendedFloatingActionButton(
                        onClick = { pickFontLauncher.launch(fontMimeTypes) },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.import_font)) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val sharedFonts = remember(fonts) { fonts.toSharedCustomFontItems() }
            val fontFamilies = remember(sharedFonts) { sharedFonts.groupByFamily() }
            Column(modifier = Modifier.fillMaxSize()) {
                SharedFontSettingsTabs(
                    selectedSection = selectedSection,
                    onSectionChange = {
                        selectedFontIds = emptySet()
                        selectedSection = it
                    },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
                )

                when (selectedSection) {
                    SharedFontSettingsSection.READER_FONTS -> {
                        if (fonts.isEmpty()) {
                            val secondaryText = if (showGoogleFontsOption) stringResource(R.string.action_browse_google_fonts) else null
                            val secondaryClick: (() -> Unit)? = if (showGoogleFontsOption) { { showGoogleFontsSheet = true } } else null

                            EmptyState(
                                title = stringResource(R.string.no_custom_fonts),
                                message = stringResource(R.string.import_fonts_desc),
                                onSelectFileClick = { pickFontLauncher.launch(fontMimeTypes) },
                                modifier = Modifier.weight(1f),
                                secondaryButtonText = secondaryText,
                                onSecondaryClick = secondaryClick
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(fontFamilies, key = { family -> family.variants.joinToString("|") { it.font.id } }) { family ->
                                    FontFamilyListItem(
                                        family = family,
                                        selectedFontIds = selectedFontIds,
                                        isSelectionMode = isFontSelectionMode,
                                        fontEntityForId = { id -> fontEntitiesById[id] },
                                        onVariantSelectionToggle = { id ->
                                            selectedFontIds = selectedFontIds.toggle(id)
                                        },
                                        onFamilySelectionToggle = {
                                            selectedFontIds = selectedFontIds.toggleAll(family.variants.map { it.font.id })
                                        },
                                        onDeleteVariant = { id ->
                                            fontEntitiesById[id]?.let { fontsPendingDelete = listOf(it) }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    SharedFontSettingsSection.APP_TEXT -> {
                        SharedAppFontSelector(
                            preference = uiState.appFontPreference,
                            customFonts = sharedFonts,
                            onPreferenceChange = viewModel::setAppFontPreference,
                            fontFamilyForPreview = { font ->
                                val file = File(font.path)
                                if (file.isFile) {
                                    runCatching { FontFamily(Font(file)) }.getOrNull()
                                } else {
                                    null
                                }
                            },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    if (fontsPendingDelete.isNotEmpty()) {
        DeleteFontsConfirmationDialog(
            fonts = fontsPendingDelete,
            onConfirm = {
                val pendingIds = fontsPendingDelete.map { it.id }
                viewModel.deleteFonts(pendingIds)
                selectedFontIds = selectedFontIds - pendingIds.toSet()
                fontsPendingDelete = emptyList()
            },
            onDismiss = {
                fontsPendingDelete = emptyList()
            }
        )
    }

    if (showGoogleFontsSheet) {
        GoogleFontsBottomSheet(
            onDismiss = { showGoogleFontsSheet = false },
            existingFonts = fonts,
            getFullFontList = { viewModel.loadGoogleFontsList(context) },
            onDownloadFont = { fontName, onComplete ->
                viewModel.downloadGoogleFont(fontName, onComplete)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleFontsBottomSheet(
    onDismiss: () -> Unit,
    existingFonts: List<CustomFontEntity>,
    getFullFontList: () -> List<String>,
    onDownloadFont: (String, () -> Unit) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var downloadingFontName by remember { mutableStateOf<String?>(null) }

    // Curated presets to show when search is empty
    val popularPresets = remember {
        listOf(
            "Merriweather", "Open Sans", "Playfair Display", "Montserrat", "Oswald", "Raleway", "Nunito",
            "Poppins", "Ubuntu", "Fira Sans", "Quicksand", "Crimson Text",
            "Literata", "EB Garamond", "Libre Baskerville", "Inter", "Work Sans"
        )
    }

    // Lazy evaluation of the full list only when typing
    val displayList = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            popularPresets
        } else {
            val allFonts = getFullFontList()
            allFonts.filter { it.contains(searchQuery, ignoreCase = true) }.take(50) // Limit to 50 for performance
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.action_browse_google_fonts),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.google_fonts_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (searchQuery.isBlank()) {
                    item {
                        Text(
                            text = stringResource(R.string.google_fonts_popular_choices),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                } else if (displayList.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.google_fonts_no_matches, searchQuery),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                items(displayList) { fontName ->
                    val isDownloaded = remember(existingFonts, fontName) {
                        existingFonts.any { it.displayName.equals(fontName, ignoreCase = true) }
                    }
                    val isDownloading = downloadingFontName == fontName

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isDownloaded && !isDownloading) {
                                downloadingFontName = fontName
                                onDownloadFont(fontName) {
                                    if (downloadingFontName == fontName) {
                                        downloadingFontName = null
                                    }
                                }
                            }
                            .background(
                                if (isDownloaded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = fontName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isDownloaded) FontWeight.Bold else FontWeight.Medium,
                            color = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )

                        Box(modifier = Modifier.padding(start = 12.dp)) {
                            when {
                                isDownloaded -> {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = stringResource(R.string.content_desc_already_downloaded),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                isDownloading -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                else -> {
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        contentDescription = stringResource(R.string.action_download),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Existing unchanged components
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FontListItem(
    font: CustomFontEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onSelectionToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val customTypeface = remember(font.path) {
        try {
            FontFamily(Font(File(font.path)))
        } catch (_: Exception) {
            null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectionToggle()
                    }
                },
                onLongClick = onSelectionToggle
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectionToggle() },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    text = font.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (!isSelectionMode) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                    .padding(12.dp)
            ) {
                if (customTypeface != null) {
                    Text(
                        text = stringResource(R.string.font_preview_text),
                        fontFamily = customTypeface,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = stringResource(R.string.font_preview_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = font.fileExtension.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FontFamilyListItem(
    family: CustomFontFamilyItem,
    selectedFontIds: Set<String>,
    isSelectionMode: Boolean,
    fontEntityForId: (String) -> CustomFontEntity?,
    onVariantSelectionToggle: (String) -> Unit,
    onFamilySelectionToggle: () -> Unit,
    onDeleteVariant: (String) -> Unit
) {
    val baseFont = remember(family) {
        family.variants.firstOrNull { it.fontFaceLabel() == "Regular" }?.font ?: family.variants.first().font
    }
    val customTypeface = remember(baseFont.path) {
        try {
            FontFamily(Font(File(baseFont.path)))
        } catch (_: Exception) {
            null
        }
    }
    val familyFontIds = remember(family) { family.variants.map { it.font.id }.toSet() }
    val isSelected = familyFontIds.any { it in selectedFontIds }
    val allSelected = familyFontIds.all { it in selectedFontIds }
    val faceSummary = remember(family) {
        buildString {
            append(family.fontFaceSummary())
            if (family.hasVariableWeightFace()) append(" - Variable weight")
            append(" - ${family.variants.size} file")
            if (family.variants.size != 1) append("s")
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onFamilySelectionToggle()
                    }
                },
                onLongClick = onFamilySelectionToggle
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { onFamilySelectionToggle() },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = family.familyName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = faceSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                    .padding(12.dp)
            ) {
                if (customTypeface != null) {
                    Text(
                        text = stringResource(R.string.font_preview_text),
                        fontFamily = customTypeface,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = stringResource(R.string.font_preview_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            family.variants.forEachIndexed { index, variant ->
                FontVariantRow(
                    variant = variant,
                    entity = fontEntityForId(variant.font.id),
                    isSelected = variant.font.id in selectedFontIds,
                    isSelectionMode = isSelectionMode,
                    onSelectionToggle = { onVariantSelectionToggle(variant.font.id) },
                    onDelete = { onDeleteVariant(variant.font.id) }
                )
                if (index != family.variants.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = if (isSelectionMode) 48.dp else 0.dp))
                }
            }
        }
    }
}

@Composable
private fun FontVariantRow(
    variant: CustomFontVariantItem,
    entity: CustomFontEntity?,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onSelectionToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = isSelectionMode) { onSelectionToggle() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectionToggle() },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = variant.fontFaceLabel(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = entity?.fileName ?: variant.font.fileName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = variant.font.fileExtension.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        if (!isSelectionMode) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun List<CustomFontEntity>.toSharedCustomFontItems(): List<CustomFontItem> {
    return filterNot { it.isDeleted }
        .sortedBy { it.displayName.lowercase() }
        .map { font ->
            CustomFontItem(
                id = font.id,
                displayName = font.displayName,
                fileName = font.fileName,
                fileExtension = font.fileExtension,
                path = font.path,
                timestamp = font.timestamp,
                isDeleted = font.isDeleted
            )
        }
}

@Composable
fun DeleteFontsConfirmationDialog(
    fonts: List<CustomFontEntity>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isSingleFont = fonts.size == 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isSingleFont) {
                    stringResource(R.string.dialog_delete_font)
                } else {
                    stringResource(R.string.dialog_delete_fonts)
                }
            )
        },
        text = {
            Text(
                if (isSingleFont) {
                    stringResource(R.string.dialog_delete_font_desc, fonts.first().displayName)
                } else {
                    stringResource(R.string.dialog_delete_fonts_desc, fonts.size)
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

private fun Set<String>.toggle(id: String): Set<String> {
    return if (id in this) this - id else this + id
}

private fun Set<String>.toggleAll(ids: List<String>): Set<String> {
    val idSet = ids.toSet()
    return if (containsAll(idSet)) this - idSet else this + idSet
}
