package com.aryan.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.aryan.reader.data.CustomFontEntity
import com.aryan.reader.epubreader.FormatSettings as AndroidFormatSettings
import com.aryan.reader.epubreader.PageInfoMode as AndroidPageInfoMode
import com.aryan.reader.epubreader.PageInfoPosition as AndroidPageInfoPosition
import com.aryan.reader.epubreader.ReaderFont as AndroidReaderFont
import com.aryan.reader.epubreader.ReaderTextAlign as AndroidReaderTextAlign
import com.aryan.reader.epubreader.SystemUiMode as AndroidSystemUiMode
import com.aryan.reader.epubreader.loadFormatSettings
import com.aryan.reader.epubreader.loadPageInfoMode
import com.aryan.reader.epubreader.loadPageInfoPosition
import com.aryan.reader.epubreader.loadPullToTurn
import com.aryan.reader.epubreader.loadPullToTurnMultiplier
import com.aryan.reader.epubreader.loadSystemUiMode
import com.aryan.reader.epubreader.savePageInfoMode
import com.aryan.reader.epubreader.savePageInfoPosition
import com.aryan.reader.epubreader.savePullToTurn
import com.aryan.reader.epubreader.savePullToTurnMultiplier
import com.aryan.reader.epubreader.saveReaderSettings
import com.aryan.reader.epubreader.saveSystemUiMode
import com.aryan.reader.pdf.savePdfSystemUiMode
import com.aryan.reader.pdf.savePdfThemeId
import com.aryan.reader.pdf.savePdfVerticalPageGapVisible
import com.aryan.reader.pdf.savePdfPageNumberOverlayVisible
import com.aryan.reader.pdf.loadPdfSystemUiMode
import com.aryan.reader.pdf.loadPdfThemeId
import com.aryan.reader.pdf.loadPdfVerticalPageGapVisible
import com.aryan.reader.pdf.loadPdfPageNumberOverlayVisible
import com.aryan.reader.shared.BuiltInPdfReaderThemes
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.PageInfoMode as SharedPageInfoMode
import com.aryan.reader.shared.PageInfoPosition as SharedPageInfoPosition
import com.aryan.reader.shared.SharedSettingsAction
import com.aryan.reader.shared.SharedSettingsDestination
import com.aryan.reader.shared.SystemUiMode as SharedSystemUiMode
import com.aryan.reader.shared.parentDestination
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import com.aryan.reader.shared.readerThemeById
import com.aryan.reader.shared.sharedSettingsHubModel
import com.aryan.reader.shared.toReaderSettings
import com.aryan.reader.shared.ui.SharedSettingsHub
import com.aryan.reader.tts.loadTtsMode
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private const val ANDROID_SETTINGS_GLOBAL_BOOK_ID = "__global_reader_defaults__"

@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    navController: NavHostController,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val customFonts by viewModel.customFonts.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsController.ttsState.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var settingsDestination by remember { mutableStateOf(SharedSettingsDestination.ROOT) }
    var showAppThemePanel by remember { mutableStateOf(false) }
    var showBehaviorDialog by remember { mutableStateOf(false) }
    var showStrictFilterDialog by remember { mutableStateOf(false) }
    var showClearBookCacheDialog by remember { mutableStateOf(false) }
    var showClearReflowCacheDialog by remember { mutableStateOf(false) }
    var showClearAllDataDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var showRecentLimitDialog by remember { mutableStateOf(false) }
    var showTtsSettingsSheet by remember { mutableStateOf(false) }
    var hideReaderAi by remember { mutableStateOf(loadHideReaderAiFeatures(context)) }
    var epubReaderDefaults by remember(context, uiState.renderMode) {
        mutableStateOf(loadAndroidEpubReaderDefaultSettings(context, uiState.renderMode))
    }
    var pdfReaderDefaults by remember(context) {
        mutableStateOf(loadAndroidPdfReaderDefaultSettings(context))
    }
    var ttsReplacementPreferences by remember(context) {
        mutableStateOf(loadTtsReplacementPreferences(context))
    }
    var ttsMode by remember(context) { mutableStateOf(loadTtsMode(context)) }

    LaunchedEffect(uiState.renderMode) {
        epubReaderDefaults = loadAndroidEpubReaderDefaultSettings(context, uiState.renderMode)
    }

    val sharedFonts = remember(customFonts) {
        customFonts.toSharedCustomFontItems()
    }

    val settingsModel = sharedSettingsHubModel(
        androidSettingsHubInput(
            uiState = uiState,
            hideReaderAi = hideReaderAi
        )
    )
    val settingsPage = settingsModel.page(settingsDestination)

    fun navigateBackFromSettings() {
        if (query.isNotBlank()) {
            query = ""
            return
        }
        val parent = settingsDestination.parentDestination()
        if (parent != null) {
            settingsDestination = parent
        } else {
            onBackClick()
        }
    }

    BackHandler(enabled = query.isNotBlank() || settingsDestination != SharedSettingsDestination.ROOT) {
        navigateBackFromSettings()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CustomTopAppBar(
                title = { Text(settingsPage.title) },
                navigationIcon = {
                    IconButton(onClick = ::navigateBackFromSettings) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.navigationBars
    ) { padding ->
        SharedSettingsHub(
            model = settingsModel,
            query = query,
            onQueryChange = { query = it },
            readerDefaultSettings = epubReaderDefaults,
            onReaderDefaultSettingsChange = { settings ->
                epubReaderDefaults = settings
                saveAndroidEpubReaderDefaultSettings(context, settings)
                viewModel.setRenderMode(settings.toAndroidRenderMode())
            },
            pdfReaderDefaultSettings = pdfReaderDefaults,
            onPdfReaderDefaultSettingsChange = { settings ->
                pdfReaderDefaults = settings
                saveAndroidPdfReaderDefaultSettings(context, settings)
            },
            ttsReplacementPreferences = ttsReplacementPreferences,
            onTtsReplacementPreferencesChange = { preferences ->
                ttsReplacementPreferences = preferences
                saveTtsReplacementPreferences(context, preferences)
            },
            customFonts = sharedFonts,
            showTopBar = false,
            destination = settingsDestination,
            onDestinationChange = { settingsDestination = it },
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
            onAction = { action ->
                when (action) {
                    SharedSettingsAction.APP_THEME -> showAppThemePanel = true
                    SharedSettingsAction.LANGUAGE -> showLanguageDialog = true
                    SharedSettingsAction.TABS_TOGGLE -> viewModel.setTabsEnabled(!uiState.isTabsEnabled)
                    SharedSettingsAction.RECENT_LIMIT -> showRecentLimitDialog = true
                    SharedSettingsAction.STRICT_FILE_FILTER -> {
                        if (uiState.useStrictFileFilter) {
                            viewModel.setStrictFileFilter(false)
                        } else {
                            showStrictFilterDialog = true
                        }
                    }
                    SharedSettingsAction.EXTERNAL_FILE_BEHAVIOR -> showBehaviorDialog = true
                    SharedSettingsAction.SCREEN_CAPTURE_PROTECTION -> {
                        val next = !uiState.isScreenCaptureProtectionEnabled
                        viewModel.setScreenCaptureProtectionEnabled(next)
                        val messageRes = if (next) {
                            R.string.banner_screen_capture_protection_on
                        } else {
                            R.string.banner_screen_capture_protection_off
                        }
                        viewModel.showBanner(context.getString(messageRes))
                    }
                    SharedSettingsAction.CUSTOM_FONTS -> navController.navigate(AppDestinations.FONTS_SCREEN_ROUTE)
                    SharedSettingsAction.SIGN_IN -> {
                        scope.launch {
                            context.findActivity()?.let { activity -> viewModel.signIn(activity) }
                        }
                    }
                    SharedSettingsAction.SIGN_OUT -> showSignOutConfirmDialog = true
                    SharedSettingsAction.CLOUD_SYNC -> {
                        if (uiState.isProUser) {
                            viewModel.setSyncEnabled(!uiState.isSyncEnabled)
                        } else {
                            showUpgradeDialog = true
                        }
                    }
                    SharedSettingsAction.FOLDER_SYNC -> viewModel.setFolderSyncEnabled(!uiState.isFolderSyncEnabled)
                    SharedSettingsAction.DEVICE_MANAGEMENT -> viewModel.showDeviceManagementForDebug()
                    SharedSettingsAction.AI_SETTINGS -> navController.navigate(AppDestinations.AI_SETTINGS_SCREEN_ROUTE)
                    SharedSettingsAction.HIDE_READER_AI -> {
                        val nextHidden = !hideReaderAi
                        saveHideReaderAiFeatures(context, nextHidden)
                        hideReaderAi = nextHidden
                    }
                    SharedSettingsAction.TTS_SETTINGS -> showTtsSettingsSheet = true
                    SharedSettingsAction.CLEAR_BOOK_CACHE -> showClearBookCacheDialog = true
                    SharedSettingsAction.CLEAR_REFLOW_CACHE -> showClearReflowCacheDialog = true
                    SharedSettingsAction.CLEAR_CLOUD_LOCAL_DATA -> showClearAllDataDialog = true
                    SharedSettingsAction.TEST_PANEL_DETECTION -> viewModel.testPanelDetection(context)
                    SharedSettingsAction.TEST_SPEECH_BUBBLE_DETECTION -> viewModel.testSpeechBubbleDetection(context)
                    SharedSettingsAction.EXPORT_LOGS -> viewModel.exportLogsToFile(context)
                    SharedSettingsAction.DEBUG_ACTIONS -> viewModel.showBanner("Debug actions remain in their existing menus.")
                    SharedSettingsAction.HELP_FEEDBACK -> navController.navigate(AppDestinations.FEEDBACK_SCREEN_ROUTE)
                    SharedSettingsAction.SUPPORT -> navController.navigate(AppDestinations.SUPPORT_PROJECT_SCREEN_ROUTE)
                    SharedSettingsAction.ABOUT -> showAboutDialog = true
                    SharedSettingsAction.PDF_READER_DEFAULTS -> viewModel.showBanner("PDF-specific OCR, annotation, and tool settings remain in the PDF reader.")
                    SharedSettingsAction.TEXT_READER_DEFAULTS,
                    SharedSettingsAction.READER_TOOLBAR,
                    SharedSettingsAction.TTS_REPLACEMENTS,
                    SharedSettingsAction.LOCAL_OVERRIDE_NOTE -> Unit
                }
            }
        )
    }

    if (showRecentLimitDialog) {
        RecentLimitDialog(
            currentLimit = uiState.recentFilesLimit,
            onSelect = { limit ->
                viewModel.setRecentFilesLimit(limit)
                showRecentLimitDialog = false
            },
            onDismiss = { showRecentLimitDialog = false }
        )
    }

    if (showBehaviorDialog) {
        ExternalFileBehaviorDialog(
            currentBehavior = uiState.externalFileBehavior,
            onDismiss = { showBehaviorDialog = false },
            onSelect = { viewModel.setExternalFileBehavior(it) }
        )
    }

    if (showStrictFilterDialog) {
        StrictFilterConfirmationDialog(
            onConfirm = {
                viewModel.setStrictFileFilter(true)
                showStrictFilterDialog = false
            },
            onDismiss = { showStrictFilterDialog = false }
        )
    }

    if (showClearBookCacheDialog) {
        DangerousFolderActionDialog(
            title = context.getString(R.string.dialog_clear_book_cache),
            message = context.getString(R.string.dialog_clear_book_cache_desc),
            onConfirm = {
                viewModel.clearBookCache()
                showClearBookCacheDialog = false
            },
            onDismiss = { showClearBookCacheDialog = false }
        )
    }

    if (showClearReflowCacheDialog) {
        DangerousFolderActionDialog(
            title = context.getString(R.string.dialog_clear_reflow_cache),
            message = context.getString(R.string.dialog_clear_reflow_cache_desc),
            onConfirm = {
                viewModel.clearReflowCache()
                showClearReflowCacheDialog = false
            },
            onDismiss = { showClearReflowCacheDialog = false }
        )
    }

    if (showClearAllDataDialog) {
        ClearAllDataConfirmationDialog(
            onConfirm = {
                viewModel.deleteAllCloudAndLocalData()
                showClearAllDataDialog = false
            },
            onDismiss = { showClearAllDataDialog = false }
        )
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(onDismiss = { showLanguageDialog = false })
    }

    if (showAppThemePanel) {
        AppThemeBottomSheet(
            uiState = uiState,
            onThemeModeChanged = viewModel::setAppThemeMode,
            onContrastOptionChanged = viewModel::setAppContrastOption,
            onTextDimFactorLightChanged = viewModel::setAppTextDimFactorLight,
            onTextDimFactorDarkChanged = viewModel::setAppTextDimFactorDark,
            onSeedColorChanged = viewModel::setAppSeedColor,
            onCustomThemeAdded = viewModel::addCustomAppTheme,
            onCustomThemeDeleted = viewModel::deleteCustomAppTheme,
            onDismiss = { showAppThemePanel = false }
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    if (showSignOutConfirmDialog) {
        SignOutConfirmationDialog(
            onConfirm = {
                viewModel.signOut()
                showSignOutConfirmDialog = false
            },
            onDismiss = { showSignOutConfirmDialog = false }
        )
    }

    if (showUpgradeDialog) {
        UpgradeDialog(
            onConfirm = {
                showUpgradeDialog = false
                navController.navigate(AppDestinations.PRO_SCREEN_ROUTE)
            },
            onDismiss = { showUpgradeDialog = false }
        )
    }

    if (showTtsSettingsSheet) {
        TtsSettingsSheet(
            isVisible = true,
            onDismiss = { showTtsSettingsSheet = false },
            currentMode = ttsMode,
            onModeChange = { mode ->
                ttsMode = mode
                viewModel.ttsController.changeTtsMode(mode.name)
            },
            currentSpeakerId = ttsState.speakerId,
            onSpeakerChange = viewModel.ttsController::changeSpeaker,
            isTtsActive = ttsState.isPlaying,
            getAuthToken = { viewModel.getAuthToken() },
            bookTitle = "Reader defaults"
        )
    }

    if (uiState.deviceLimitState.isLimitReached) {
        DeviceManagementScreen(
            devices = uiState.deviceLimitState.registeredDevices,
            onRemoveDevice = { deviceId -> viewModel.replaceDevice(deviceId) },
            isReplacing = uiState.isReplacingDevice
        )
    }
}

@Composable
private fun RecentLimitDialog(
    currentLimit: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recent files limit") },
        text = {
            androidx.compose.foundation.layout.Column {
                listOf(0, 10, 20, 50, 100).forEach { limit ->
                    TextButton(onClick = { onSelect(limit) }) {
                        val label = if (limit == 0) "No limit" else "$limit files"
                        Text(if (currentLimit == limit) "$label selected" else label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun loadAndroidEpubReaderDefaultSettings(
    context: Context,
    renderMode: RenderMode
): ReaderSettings {
    val format = loadFormatSettings(context, ANDROID_SETTINGS_GLOBAL_BOOK_ID, isLocal = false)
    val horizontalMargin = (48f * format.horizontalMargin).roundToInt().coerceIn(0, 160)
    val verticalMargin = (48f * format.verticalMargin).roundToInt().coerceIn(0, 160)
    val base = ReaderSettings(
        fontSize = (18f * format.fontSize).roundToInt().coerceIn(12, 42),
        lineSpacing = (1.45f * format.lineHeight).coerceIn(1.0f, 2.8f),
        margin = max(horizontalMargin, verticalMargin),
        readingMode = renderMode.toSharedReaderReadingMode(),
        textAlign = format.textAlign.toSharedReaderTextAlign(),
        fontFamily = format.toSharedFontFamilyName(),
        paragraphSpacing = format.paragraphGap.coerceIn(0.5f, 2.5f),
        imageScale = format.imageSize.coerceIn(0.5f, 2.0f),
        horizontalMargin = horizontalMargin,
        verticalMargin = verticalMargin,
        themeId = loadReaderThemeId(context),
        textureAlpha = (1f - loadGlobalTextureTransparency(context)).coerceIn(0f, 1f),
        customFontPath = format.customPath?.takeIf { it.isNotBlank() },
        systemUiMode = loadSystemUiMode(context).toSharedSystemUiMode(),
        pageInfoMode = loadPageInfoMode(context).toSharedPageInfoMode(),
        pageInfoPosition = loadPageInfoPosition(context).toSharedPageInfoPosition(),
        seamlessChapterNavigation = loadPullToTurn(context),
        chapterTurnDragMultiplier = loadPullToTurnMultiplier(context)
    )
    return readerThemeById(base.themeId)?.toReaderSettings(base) ?: base
}

private fun loadAndroidPdfReaderDefaultSettings(
    context: Context
): ReaderSettings {
    val base = ReaderSettings(
        themeId = loadPdfThemeId(context),
        textureAlpha = (1f - loadGlobalTextureTransparency(context)).coerceIn(0f, 1f),
        systemUiMode = loadPdfSystemUiMode(context).toSharedSystemUiMode(),
        pdfVerticalPageGapVisible = loadPdfVerticalPageGapVisible(context),
        pdfPageNumberOverlayVisible = loadPdfPageNumberOverlayVisible(context)
    )
    return BuiltInPdfReaderThemes.firstOrNull { it.id == base.themeId }?.toReaderSettings(base) ?: base
}

private fun saveAndroidEpubReaderDefaultSettings(
    context: Context,
    settings: ReaderSettings
) {
    saveReaderSettings(
        context = context,
        fontSize = (settings.fontSize / 18f).coerceIn(0.65f, 2.4f),
        lineHeight = (settings.lineSpacing / 1.45f).coerceIn(0.7f, 2.0f),
        paragraphGap = settings.paragraphSpacing.coerceIn(0.5f, 2.5f),
        imageSize = settings.imageScale.coerceIn(0.5f, 2.0f),
        horizontalMargin = (settings.resolvedHorizontalMargin / 48f).coerceIn(0f, 3.4f),
        verticalMargin = (settings.resolvedVerticalMargin / 48f).coerceIn(0f, 3.4f),
        fontFamily = settings.toAndroidReaderFont(),
        customFontPath = settings.customFontPath,
        textAlign = settings.textAlign.toAndroidTextAlign()
    )
    saveSystemUiMode(context, settings.systemUiMode.toAndroidSystemUiMode())
    savePageInfoMode(context, settings.pageInfoMode.toAndroidPageInfoMode())
    savePageInfoPosition(context, settings.pageInfoPosition.toAndroidPageInfoPosition())
    savePullToTurn(context, settings.seamlessChapterNavigation)
    savePullToTurnMultiplier(context, settings.chapterTurnDragMultiplier)
    saveReaderThemeId(context, settings.themeId ?: "system")
    saveGlobalTextureTransparency(context, 1f - settings.textureAlpha.coerceIn(0f, 1f))
}

private fun saveAndroidPdfReaderDefaultSettings(
    context: Context,
    settings: ReaderSettings
) {
    savePdfSystemUiMode(context, settings.systemUiMode.toAndroidSystemUiMode())
    savePdfThemeId(context, settings.themeId ?: "no_theme")
    savePdfVerticalPageGapVisible(context, settings.pdfVerticalPageGapVisible)
    savePdfPageNumberOverlayVisible(context, settings.pdfPageNumberOverlayVisible)
    saveGlobalTextureTransparency(context, 1f - settings.textureAlpha.coerceIn(0f, 1f))
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

private fun AndroidFormatSettings.toSharedFontFamilyName(): String {
    return customPath?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() }
        ?: when (font) {
            AndroidReaderFont.ORIGINAL -> "Default"
            AndroidReaderFont.MERRIWEATHER,
            AndroidReaderFont.LORA -> "Serif"
            AndroidReaderFont.LATO,
            AndroidReaderFont.LEXEND -> "Sans"
            AndroidReaderFont.ROBOTO_MONO -> "Mono"
        }
}

private fun AndroidReaderTextAlign.toSharedReaderTextAlign(): SharedReaderTextAlign {
    return when (this) {
        AndroidReaderTextAlign.JUSTIFY -> SharedReaderTextAlign.JUSTIFY
        AndroidReaderTextAlign.DEFAULT,
        AndroidReaderTextAlign.LEFT -> SharedReaderTextAlign.START
    }
}

private fun ReaderSettings.toAndroidReaderFont(): AndroidReaderFont {
    return when (fontFamily) {
        "Serif" -> AndroidReaderFont.LORA
        "Sans" -> AndroidReaderFont.LATO
        "Mono" -> AndroidReaderFont.ROBOTO_MONO
        else -> AndroidReaderFont.ORIGINAL
    }
}

private fun SharedReaderTextAlign.toAndroidTextAlign(): AndroidReaderTextAlign {
    return when (this) {
        SharedReaderTextAlign.JUSTIFY -> AndroidReaderTextAlign.JUSTIFY
        SharedReaderTextAlign.CENTER,
        SharedReaderTextAlign.START -> AndroidReaderTextAlign.LEFT
    }
}

private fun RenderMode.toSharedReaderReadingMode(): ReaderReadingMode {
    return when (this) {
        RenderMode.PAGINATED -> ReaderReadingMode.PAGINATED
        RenderMode.VERTICAL_SCROLL -> ReaderReadingMode.VERTICAL
    }
}

private fun ReaderSettings.toAndroidRenderMode(): RenderMode {
    return when (readingMode) {
        ReaderReadingMode.PAGINATED -> RenderMode.PAGINATED
        ReaderReadingMode.VERTICAL -> RenderMode.VERTICAL_SCROLL
    }
}

private fun AndroidSystemUiMode.toSharedSystemUiMode(): SharedSystemUiMode {
    return SharedSystemUiMode.valueOf(name)
}

private fun SharedSystemUiMode.toAndroidSystemUiMode(): AndroidSystemUiMode {
    return AndroidSystemUiMode.valueOf(name)
}

private fun AndroidPageInfoMode.toSharedPageInfoMode(): SharedPageInfoMode {
    return SharedPageInfoMode.valueOf(name)
}

private fun SharedPageInfoMode.toAndroidPageInfoMode(): AndroidPageInfoMode {
    return AndroidPageInfoMode.valueOf(name)
}

private fun AndroidPageInfoPosition.toSharedPageInfoPosition(): SharedPageInfoPosition {
    return SharedPageInfoPosition.valueOf(name)
}

private fun SharedPageInfoPosition.toAndroidPageInfoPosition(): AndroidPageInfoPosition {
    return AndroidPageInfoPosition.valueOf(name)
}
