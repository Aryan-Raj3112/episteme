@file:Suppress("KotlinConstantConditions")

package com.aryan.reader

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aryan.reader.data.CustomFontEntity
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.ui.SharedGoogleFontsBottomSheet
import com.aryan.reader.shared.ui.SharedGoogleFontsLabels
import com.aryan.reader.shared.ui.SharedMobileFontsScreen
import com.aryan.reader.shared.ui.SharedMobileFontsStrings
import java.io.File

@Composable
fun FontsScreen(viewModel: MainViewModel, onBackClick: () -> Unit) {
    val fonts by viewModel.customFonts.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sharedFonts = remember(fonts) { fonts.toSharedCustomFontItems() }
    val showGoogleFontsOption = !(BuildConfig.FLAVOR == "oss" && BuildConfig.IS_OFFLINE)
    val pickFontLauncher = rememberFilePickerLauncher(viewModel::importFonts)
    val fontMimeTypes = remember { supportedFontMimeTypes() }

    SharedMobileFontsScreen(
        fonts = sharedFonts,
        appFontPreference = uiState.appFontPreference,
        showGoogleFontsOption = showGoogleFontsOption,
        isLoading = uiState.isLoading,
        strings = SharedMobileFontsStrings(
            title = stringResource(R.string.custom_fonts),
            backDescription = stringResource(R.string.action_back),
            selectAllDescription = stringResource(R.string.select_all),
            selectedCount = { context.getString(R.string.items_selected_count, it) },
            clearSelectionDescription = stringResource(R.string.clear_selection),
            deleteDescription = stringResource(R.string.action_delete),
            googleFonts = stringResource(R.string.google_fonts),
            importFont = stringResource(R.string.import_font),
            emptyTitle = stringResource(R.string.no_custom_fonts),
            emptyMessage = stringResource(R.string.import_fonts_desc),
            selectFile = stringResource(R.string.empty_select_file),
            browseGoogleFonts = stringResource(R.string.action_browse_google_fonts),
            previewText = stringResource(R.string.font_preview_text),
            previewError = stringResource(R.string.font_preview_error),
            variableWeight = "Variable weight",
            fileCount = { count -> "$count file${if (count == 1) "" else "s"}" },
            deleteSingleTitle = stringResource(R.string.dialog_delete_font),
            deleteMultipleTitle = stringResource(R.string.dialog_delete_fonts),
            deleteSingleBody = { context.getString(R.string.dialog_delete_font_desc, it) },
            deleteMultipleBody = { context.getString(R.string.dialog_delete_fonts_desc, it) },
            cancelAction = stringResource(R.string.action_cancel),
        ),
        onBackClick = onBackClick,
        onImportFonts = { pickFontLauncher.launch(fontMimeTypes) },
        onDeleteFonts = viewModel::deleteFonts,
        onAppFontPreferenceChange = viewModel::setAppFontPreference,
        fontFamilyForPreview = { font ->
            val file = File(font.path)
            if (file.isFile) runCatching { FontFamily(Font(file)) }.getOrNull() else null
        },
        googleFontsSheet = { onDismiss ->
            GoogleFontsBottomSheet(
                onDismiss = onDismiss,
                existingFonts = fonts,
                getFullFontList = { viewModel.loadGoogleFontsList(context) },
                onDownloadFont = { fontName, onComplete -> viewModel.downloadGoogleFont(fontName, onComplete) },
            )
        },
        platformBackHandler = { enabled, onBack -> BackHandler(enabled = enabled, onBack = onBack) },
        modifier = Modifier.statusBarsPadding(),
    )
}

@Composable
fun GoogleFontsBottomSheet(
    onDismiss: () -> Unit,
    existingFonts: List<CustomFontEntity>,
    getFullFontList: () -> List<String>,
    onDownloadFont: (String, () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    SharedGoogleFontsBottomSheet(
        existingFontNames = existingFonts.map { it.displayName },
        getFullFontList = getFullFontList,
        onDownloadFont = onDownloadFont,
        labels = SharedGoogleFontsLabels(
            title = stringResource(R.string.action_browse_google_fonts),
            searchPlaceholder = stringResource(R.string.google_fonts_search_placeholder),
            popularChoices = stringResource(R.string.google_fonts_popular_choices),
            noMatches = { query -> context.getString(R.string.google_fonts_no_matches, query) },
            alreadyDownloadedContentDescription = stringResource(R.string.content_desc_already_downloaded),
            downloadContentDescription = stringResource(R.string.action_download),
        ),
        onDismiss = onDismiss,
    )
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
                isDeleted = font.isDeleted,
            )
        }
}
