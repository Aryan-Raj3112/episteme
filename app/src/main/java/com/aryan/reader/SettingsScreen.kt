package com.aryan.reader

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.aryan.reader.data.CustomFontEntity
import com.aryan.reader.epubreader.FormatSettings as AndroidFormatSettings
import com.aryan.reader.epubreader.ReaderFont as AndroidReaderFont
import com.aryan.reader.epubreader.ReaderTextAlign as AndroidReaderTextAlign
import com.aryan.reader.epubreader.loadFormatSettings
import com.aryan.reader.epubreader.loadPageInfoMode
import com.aryan.reader.epubreader.loadPageInfoPosition
import com.aryan.reader.epubreader.loadPullToTurn
import com.aryan.reader.epubreader.loadPullToTurnMultiplier
import com.aryan.reader.epubreader.loadSystemUiMode
import com.aryan.reader.epubreader.loadTapToNavigateSetting
import com.aryan.reader.epubreader.savePageInfoMode
import com.aryan.reader.epubreader.savePageInfoPosition
import com.aryan.reader.epubreader.savePullToTurn
import com.aryan.reader.epubreader.savePullToTurnMultiplier
import com.aryan.reader.epubreader.saveReaderSettings
import com.aryan.reader.epubreader.saveSystemUiMode
import com.aryan.reader.epubreader.saveTapToNavigateSetting
import com.aryan.reader.pdf.savePdfSystemUiMode
import com.aryan.reader.pdf.savePdfThemeId
import com.aryan.reader.pdf.savePdfVerticalPageGapVisible
import com.aryan.reader.pdf.savePdfPageNumberOverlayVisible
import com.aryan.reader.pdf.loadPdfSystemUiMode
import com.aryan.reader.pdf.loadPdfThemeId
import com.aryan.reader.pdf.loadPdfVerticalPageGapVisible
import com.aryan.reader.pdf.loadPdfPageNumberOverlayVisible
import com.aryan.reader.loadPdfReverseColorMode
import com.aryan.reader.savePdfReverseColorMode
import com.aryan.reader.shared.BuiltInPdfReaderThemes
import com.aryan.reader.shared.CloudFolderSyncSelection
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.SharedSettingsAction
import com.aryan.reader.shared.MobileSettingsMutation
import com.aryan.reader.shared.MobileSettingsMutationState
import com.aryan.reader.shared.MobileStrictFileFilterEffect
import com.aryan.reader.shared.planMobileSettingsMutation
import com.aryan.reader.shared.SharedSettingsDestination
import com.aryan.reader.shared.parentDestination
import com.aryan.reader.shared.toReaderSettingsFontFamily
import com.aryan.reader.shared.toSharedReaderTextAlign
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import com.aryan.reader.shared.reader.pullToTurnEnabled
import com.aryan.reader.shared.readerThemeById
import com.aryan.reader.shared.sharedSettingsHubModel
import com.aryan.reader.shared.toReaderSettings
import com.aryan.reader.shared.ui.SharedSettingsHub
import com.aryan.reader.shared.ui.LocalSharedStringResolver
import com.aryan.reader.shared.ui.SharedStringResolver
import com.aryan.reader.tts.loadTtsMode
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private const val ANDROID_SETTINGS_GLOBAL_BOOK_ID = "__global_reader_defaults__"

private fun androidSettingsLiteralResource(context: Context, literal: String): Int? = when (literal) {
    "Reader settings" -> R.string.settings_hub_literal_0
    "Global defaults for text, EPUB, PDF, toolbar, and speech" -> R.string.settings_hub_literal_1
    "App & library" -> R.string.settings_hub_literal_2
    "App preferences, imports, tabs, and local library behavior" -> R.string.settings_hub_literal_3
    "Sync & accounts" -> R.string.settings_hub_literal_4
    "Sign-in, cloud sync, and folder backup" -> R.string.settings_hub_literal_5
    "AI & TTS" -> R.string.settings_hub_literal_6
    "Reader AI, keys, models, voices, and speech preferences" -> R.string.settings_hub_literal_7
    "Storage & advanced" -> R.string.settings_hub_literal_8
    "Caches and diagnostic tools" -> R.string.settings_hub_literal_9
    "Help" -> R.string.settings_hub_literal_10
    "Feedback, support, and app information" -> R.string.settings_hub_literal_11
    "Extra" -> R.string.settings_hub_literal_12
    "Overflow options, maintenance, and diagnostics" -> R.string.settings_hub_literal_13
    "Settings" -> R.string.settings_hub_literal_14
    "Global defaults, app preferences, and advanced options" -> R.string.settings_hub_literal_15
    "EPUB & Text" -> R.string.settings_hub_literal_16
    "Defaults for reflowable reading, layout, EPUB themes, and reader tools" -> R.string.settings_hub_literal_17
    "PDF & Comics" -> R.string.settings_hub_literal_18
    "Defaults for fixed-layout reading, PDF themes, and PDF-specific tools" -> R.string.settings_hub_literal_19
    "App Preferences" -> R.string.settings_hub_literal_20
    "App theme and general app behavior" -> R.string.settings_hub_literal_21
    "Library & Files" -> R.string.settings_hub_literal_22
    "Recent files and local reading fonts" -> R.string.settings_hub_literal_23
    "Sync & Accounts" -> R.string.settings_hub_literal_24
    "Sign-in, cloud sync, folder sync, and devices" -> R.string.settings_hub_literal_25
    "More-menu options, maintenance actions, diagnostics, and app info" -> R.string.settings_hub_literal_26
    "Help & About" -> R.string.settings_hub_literal_27
    "Feedback, support, project information, and licenses" -> R.string.settings_hub_literal_28
    "Format Defaults" -> R.string.settings_hub_literal_29
    "Font, size, spacing, margins, alignment, and reading mode" -> R.string.settings_hub_literal_30
    "EPUB Theme & Texture" -> R.string.settings_hub_literal_31
    "Default EPUB reading theme, paper texture, and texture strength" -> R.string.settings_hub_literal_32
    "Visual Defaults" -> R.string.settings_hub_literal_33
    "Page indicators, system UI, images, and chapter-turn behavior" -> R.string.settings_hub_literal_34
    "PDF Theme Defaults" -> R.string.settings_hub_literal_35
    "Default PDF and comic theme where fixed-layout appearance is supported" -> R.string.settings_hub_literal_36
    "PDF Reader Tools" -> R.string.settings_hub_literal_37
    "Auto-scroll, OCR, annotation, and PDF-only tools remain in the PDF reader" -> R.string.settings_hub_literal_38
    "Reader Toolbar Defaults" -> R.string.settings_hub_literal_39
    "Visible tools, bottom-bar actions, and reader overflow tools" -> R.string.settings_hub_literal_40
    "Global TTS Replacements" -> R.string.settings_hub_literal_41
    "Words and phrases replaced only during speech playback" -> R.string.settings_hub_literal_42
    "Format, EPUB theme, visual defaults, and reader tools" -> R.string.settings_hub_literal_43
    "Separate PDF theme and fixed-layout reader defaults" -> R.string.settings_hub_literal_44
    "More-menu options, maintenance, diagnostics, and app info" -> R.string.settings_hub_literal_45
    "Format defaults" -> R.string.settings_hub_literal_46
    "Font, size, line spacing, margins, alignment, and reading mode" -> R.string.settings_hub_literal_47
    "Theme and texture" -> R.string.settings_hub_literal_48
    "Reading theme, texture, and page feel for new books" -> R.string.settings_hub_literal_49
    "Visual defaults" -> R.string.settings_hub_literal_50
    "System UI, page info, images, and chapter-turn behavior" -> R.string.settings_hub_literal_51
    "PDF theme defaults" -> R.string.settings_hub_literal_52
    "PDF and comic theme defaults, separate from EPUB themes" -> R.string.settings_hub_literal_53
    "PDF reader tools" -> R.string.settings_hub_literal_54
    "Auto-scroll, OCR, annotations, and PDF-only tools" -> R.string.settings_hub_literal_55
    "Text and EPUB defaults" -> R.string.settings_hub_literal_56
    "Format, EPUB theme, texture, visual behavior, and text layout" -> R.string.settings_hub_literal_57
    "PDF and comic defaults" -> R.string.settings_hub_literal_58
    "PDF theme, visual defaults, tools, auto-scroll, OCR, and annotation behavior where available" -> R.string.settings_hub_literal_59
    "Reader toolbar and tools" -> R.string.settings_hub_literal_60
    "Choose visible tools, bottom-bar tools, and reader overflow tools" -> R.string.settings_hub_literal_61
    "Per-book overrides" -> R.string.settings_hub_literal_62
    "Local overrides are available from the active reader screen and still win for that book." -> R.string.settings_hub_literal_63
    "App theme" -> R.string.settings_hub_literal_64
    "Theme mode, contrast, reading text dimming, and custom app colors" -> R.string.settings_hub_literal_65
    "Custom fonts" -> R.string.settings_hub_literal_66
    "Import, manage, and reuse local reading fonts" -> R.string.settings_hub_literal_67
    "Recent files limit" -> R.string.settings_hub_literal_68
    "Control how many recent books appear on Home" -> R.string.settings_hub_literal_69
    "Sign out" -> R.string.settings_hub_literal_70
    "Disconnect this device from your account" -> R.string.settings_hub_literal_71
    "Sign in" -> R.string.settings_hub_literal_72
    "Connect sync and account features" -> R.string.settings_hub_literal_73
    "Cloud library sync" -> R.string.settings_hub_literal_74
    "Folder backup and sync" -> R.string.settings_hub_literal_75
    "Keep selected local folders represented in the library" -> R.string.settings_hub_literal_76
    "Device management" -> R.string.settings_hub_literal_77
    "Inspect registered devices for this account" -> R.string.settings_hub_literal_78
    "AI keys and models" -> R.string.settings_hub_literal_79
    "Configure reader AI and cloud TTS model access" -> R.string.settings_hub_literal_80
    "TTS voice settings" -> R.string.settings_hub_literal_81
    "Global TTS replacements" -> R.string.settings_hub_literal_82
    "Clear book cache" -> R.string.settings_hub_literal_83
    "Remove generated book cache files and recreate them on demand" -> R.string.settings_hub_literal_84
    "Clear reflow cache" -> R.string.settings_hub_literal_85
    "Remove generated PDF text-view files" -> R.string.settings_hub_literal_86
    "Test panel detection" -> R.string.settings_hub_literal_87
    "Run the local panel-detection diagnostic" -> R.string.settings_hub_literal_88
    "Test speech-bubble detection" -> R.string.settings_hub_literal_89
    "Run the local speech-bubble detection diagnostic" -> R.string.settings_hub_literal_90
    "Export logs" -> R.string.settings_hub_literal_91
    "Export recent diagnostic logs" -> R.string.settings_hub_literal_92
    "Clear cloud and local data" -> R.string.settings_hub_literal_93
    "Delete cloud records and matching local library data" -> R.string.settings_hub_literal_94
    "Share recent diagnostic logs collected by the app" -> R.string.settings_hub_literal_95
    "Screen capture protection" -> R.string.settings_hub_literal_96
    "Block screenshots and screen recording on sensitive reader screens" -> R.string.settings_hub_literal_97
    "External file behavior" -> R.string.settings_hub_literal_98
    "Choose whether external opens are copied into the app library" -> R.string.settings_hub_literal_99
    "Strict file filter" -> R.string.settings_hub_literal_100
    "Use only known reader file types in import pickers" -> R.string.settings_hub_literal_101
    "Use PDF filenames" -> R.string.settings_hub_literal_102
    "Reader tabs" -> R.string.settings_hub_literal_103
    "Reader AI visibility" -> R.string.settings_hub_literal_104
    "Help and feedback" -> R.string.settings_hub_literal_105
    "Send feedback or report an issue" -> R.string.settings_hub_literal_106
    "Support project" -> R.string.settings_hub_literal_107
    "Open support options for the project" -> R.string.settings_hub_literal_108
    "About" -> R.string.settings_hub_literal_109
    "Version, source, licenses, and project information" -> R.string.settings_hub_literal_110
    "A Pro account is required for cloud sync." -> R.string.settings_hub_cloud_sync_pro_required
    "Sync library metadata across signed-in devices." -> R.string.settings_hub_cloud_sync_ready
    "Link Google to enable cloud sync." -> R.string.settings_hub_cloud_sync_link_google
    "Authorize Google Drive to enable sync." -> R.string.settings_hub_cloud_sync_drive_auth
    "Choose an iOS voice, speech rate, and pitch" -> R.string.settings_hub_tts_ios
    "Choose cloud or device voices and speech behavior" -> R.string.settings_hub_tts_android
    "PDF lists and tabs show filenames instead of embedded titles." -> R.string.settings_hub_pdf_filenames
    "PDF lists and tabs prefer embedded titles when available." -> R.string.settings_hub_pdf_titles
    "Opening PDFs keeps active tabs." -> R.string.settings_hub_tabs_on
    "PDFs replace the active reader session." -> R.string.settings_hub_tabs_off
    "Reader AI tools are hidden." -> R.string.settings_hub_ai_hidden
    "Reader AI tools are shown where available." -> R.string.settings_hub_ai_visible
    else -> null
}

@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    navController: NavHostController,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onManageIncomingFolder: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cloudFolderRootStats by viewModel.cloudFolderRootStats.collectAsStateWithLifecycle()
    val cloudFolderRoots by viewModel.cloudFolderRoots.collectAsStateWithLifecycle()
    val cloudFolderBindings by viewModel.cloudFolderBindings.collectAsStateWithLifecycle()
    val cloudFolderConflicts by viewModel.cloudFolderConflicts.collectAsStateWithLifecycle()
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
    var showCloudFolderSyncDialog by remember { mutableStateOf(false) }
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
    var cloudFolderSelection by remember(context, uiState.currentUser?.uid) {
        mutableStateOf(
            uiState.currentUser?.uid?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { accountId -> CloudFolderSyncPrefs.load(context, accountId) }
                ?: CloudFolderSyncSelection.Default
        )
    }

    LaunchedEffect(uiState.currentUser?.uid, showCloudFolderSyncDialog) {
        if (showCloudFolderSyncDialog) {
            // Counts come from the repository manifest/scan, not only from
            // indexed library rows, so direct-cloud folders remain visible.
            viewModel.refreshCloudFolderSyncState()
        }
    }

    val addCloudFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            // Cloud-folder settings owns cloud registration; preserve the
            // legacy local-library indexing switch as an explicit choice.
            viewModel.addSyncedFolder(uri, indexInLibrary = false)
            // Reopen the secondary surface after the picker so the newly
            // added folder and its computed inventory are immediately visible.
            showCloudFolderSyncDialog = true
        }
    }
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

    val cloudFolderOptions = remember(
        uiState.syncedFolders,
        uiState.rawLibraryFiles,
        cloudFolderRootStats,
        cloudFolderRoots,
        cloudFolderBindings,
    ) {
        cloudFolderSyncFolderOptions(
            folders = uiState.syncedFolders,
            indexedFiles = uiState.rawLibraryFiles,
            repositoryStats = cloudFolderRootStats,
            repositoryRoots = cloudFolderRoots,
            deviceBindings = cloudFolderBindings,
        )
    }

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
                title = {
                    Text(androidSettingsLiteralResource(context, settingsPage.title)?.let(context::getString) ?: settingsPage.title)
                },
                navigationIcon = {
                    IconButton(onClick = ::navigateBackFromSettings) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.navigationBars
    ) { padding ->
        CompositionLocalProvider(
            LocalSharedStringResolver provides SharedStringResolver(
                resolveLiteral = { literal ->
                    androidSettingsLiteralResource(context, literal)?.let(context::getString)
                }
            )
        ) {
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
                // Folder sync is a secondary settings surface now.  Do not
                // toggle the legacy local-indexing switch merely by opening
                // the cloud-folder selection dialog.
                val portableMutation = if (action == SharedSettingsAction.FOLDER_SYNC) {
                    null
                } else {
                    planMobileSettingsMutation(
                        action = action,
                        state = MobileSettingsMutationState(
                            tabsEnabled = uiState.isTabsEnabled,
                            strictFileFilterEnabled = uiState.useStrictFileFilter,
                            pdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName,
                            folderSyncEnabled = uiState.isFolderSyncEnabled,
                            hideReaderAi = hideReaderAi,
                        ),
                    )
                }
                when (portableMutation) {
                    is MobileSettingsMutation.SetTabsEnabled ->
                        viewModel.setTabsEnabled(portableMutation.enabled)
                    is MobileSettingsMutation.ChangeStrictFileFilter -> when (portableMutation.effect) {
                        MobileStrictFileFilterEffect.DISABLE -> viewModel.setStrictFileFilter(false)
                        MobileStrictFileFilterEffect.CONFIRM_ENABLE -> showStrictFilterDialog = true
                    }
                    is MobileSettingsMutation.SetPdfFileNameAsDisplayName ->
                        viewModel.setUsePdfFileNameAsDisplayName(portableMutation.enabled)
                    is MobileSettingsMutation.SetFolderSyncEnabled ->
                        viewModel.setFolderSyncEnabled(portableMutation.enabled)
                    is MobileSettingsMutation.SetHideReaderAi -> {
                        saveHideReaderAiFeatures(context, portableMutation.hidden)
                        hideReaderAi = portableMutation.hidden
                    }
                    null -> Unit
                }
                when (action) {
                    SharedSettingsAction.APP_THEME -> showAppThemePanel = true
                    SharedSettingsAction.LANGUAGE -> showLanguageDialog = true
                    SharedSettingsAction.RECENT_LIMIT -> showRecentLimitDialog = true
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
                    SharedSettingsAction.CUSTOM_FONTS -> navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.FONTS)
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
                    SharedSettingsAction.FOLDER_SYNC -> {
                        cloudFolderSelection = viewModel.cloudFolderSyncSelection()
                        showCloudFolderSyncDialog = true
                    }
                    SharedSettingsAction.DEVICE_MANAGEMENT -> viewModel.showDeviceManagementForDebug()
                    SharedSettingsAction.AI_SETTINGS -> navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.AI_SETTINGS)
                    SharedSettingsAction.HIDE_READER_AI -> Unit
                    SharedSettingsAction.TTS_SETTINGS -> showTtsSettingsSheet = true
                    SharedSettingsAction.CLEAR_BOOK_CACHE -> showClearBookCacheDialog = true
                    SharedSettingsAction.CLEAR_REFLOW_CACHE -> showClearReflowCacheDialog = true
                    SharedSettingsAction.CLEAR_CLOUD_LOCAL_DATA -> showClearAllDataDialog = true
                    SharedSettingsAction.TEST_PANEL_DETECTION -> viewModel.testPanelDetection(context)
                    SharedSettingsAction.TEST_SPEECH_BUBBLE_DETECTION -> viewModel.testSpeechBubbleDetection(context)
                    SharedSettingsAction.EXPORT_LOGS -> viewModel.exportLogsToFile(context)
                    SharedSettingsAction.DEBUG_ACTIONS -> viewModel.showBanner(context.getString(R.string.debug_actions_existing_menus))
                    SharedSettingsAction.HELP_FEEDBACK -> navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.FEEDBACK)
                    SharedSettingsAction.SUPPORT -> navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.SUPPORT_PROJECT)
                    SharedSettingsAction.ABOUT -> showAboutDialog = true
                    SharedSettingsAction.PDF_READER_DEFAULTS -> viewModel.showBanner(context.getString(R.string.pdf_specific_settings_existing_reader))
                    SharedSettingsAction.TEXT_READER_DEFAULTS,
                    SharedSettingsAction.READER_TOOLBAR,
                    SharedSettingsAction.TTS_REPLACEMENTS,
                    SharedSettingsAction.LOCAL_OVERRIDE_NOTE,
                    SharedSettingsAction.TABS_TOGGLE,
                    SharedSettingsAction.STRICT_FILE_FILTER,
                    SharedSettingsAction.PDF_FILENAME_DISPLAY_NAME -> Unit
                }
            }
            )
        }
    }

    if (showCloudFolderSyncDialog) {
        CloudFolderSyncSettingsDialog(
            folders = cloudFolderOptions,
            selection = cloudFolderSelection,
            localFolderIndexingEnabled = uiState.isFolderSyncEnabled,
            conflicts = cloudFolderConflicts,
            onSelectionChange = { selection ->
                val normalized = selection.normalized()
                cloudFolderSelection = normalized
                viewModel.setCloudFolderSyncSelection(normalized)
            },
            onLocalFolderIndexingChange = viewModel::setFolderSyncEnabled,
            onConflictResolution = viewModel::resolveCloudFolderConflict,
            onAddFolder = {
                showCloudFolderSyncDialog = false
                try {
                    addCloudFolderLauncher.launch(null)
                } catch (_: ActivityNotFoundException) {
                    viewModel.showBanner(
                        context.getString(R.string.error_folder_selection_unsupported),
                        isError = true,
                    )
                }
            },
            onSetMaterializationMode = { rootId, mode ->
                viewModel.setCloudFolderMaterializationMode(rootId, mode)
            },
            onManageIncomingFolder = { rootId ->
                showCloudFolderSyncDialog = false
                onManageIncomingFolder(rootId)
            },
            onDismiss = { showCloudFolderSyncDialog = false },
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
                navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.PRO)
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
            bookTitle = context.getString(R.string.reader_defaults)
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
        title = { Text(stringResource(R.string.options_recent_limit)) },
        text = {
            androidx.compose.foundation.layout.Column {
                listOf(0, 10, 20, 50, 100).forEach { limit ->
                    TextButton(onClick = { onSelect(limit) }) {
                        val label = if (limit == 0) {
                            stringResource(R.string.options_no_limit)
                        } else {
                            stringResource(R.string.options_files_limit, limit)
                        }
                        Text(if (currentLimit == limit) stringResource(R.string.option_selected_format, label) else label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
        fontWeight = format.fontWeight,
        letterSpacing = format.letterSpacing,
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
        systemUiMode = loadSystemUiMode(context),
        pageInfoMode = loadPageInfoMode(context),
        pageInfoPosition = loadPageInfoPosition(context),
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
        systemUiMode = loadPdfSystemUiMode(context),
        pdfVerticalPageGapVisible = loadPdfVerticalPageGapVisible(context),
        pdfPageNumberOverlayVisible = loadPdfPageNumberOverlayVisible(context),
        pdfReverseColorMode = loadPdfReverseColorMode(context),
        pdfPreserveImageColors = loadExcludeImages(context),
        tapToNavigateEnabled = loadTapToNavigateSetting(context),
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
        textAlign = settings.textAlign.toAndroidTextAlign(),
        fontWeight = settings.fontWeight,
        letterSpacing = settings.letterSpacing
    )
    saveSystemUiMode(context, settings.systemUiMode)
    savePageInfoMode(context, settings.pageInfoMode)
    savePageInfoPosition(context, settings.pageInfoPosition)
    savePullToTurn(context, settings.pullToTurnEnabled)
    savePullToTurnMultiplier(context, settings.chapterTurnDragMultiplier)
    saveReaderThemeId(context, settings.themeId ?: "system")
    saveGlobalTextureTransparency(context, 1f - settings.textureAlpha.coerceIn(0f, 1f))
}

private fun saveAndroidPdfReaderDefaultSettings(
    context: Context,
    settings: ReaderSettings
) {
    savePdfSystemUiMode(context, settings.systemUiMode)
    savePdfThemeId(context, settings.themeId ?: "no_theme")
    savePdfVerticalPageGapVisible(context, settings.pdfVerticalPageGapVisible)
    savePdfPageNumberOverlayVisible(context, settings.pdfPageNumberOverlayVisible)
    savePdfReverseColorMode(context, settings.pdfReverseColorMode)
    saveExcludeImages(context, settings.pdfPreserveImageColors)
    saveTapToNavigateSetting(context, settings.tapToNavigateEnabled)
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
        ?: font.toReaderSettingsFontFamily()
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
        SharedReaderTextAlign.RIGHT -> AndroidReaderTextAlign.RIGHT
        SharedReaderTextAlign.LEFT -> AndroidReaderTextAlign.LEFT
        SharedReaderTextAlign.START -> AndroidReaderTextAlign.DEFAULT
        SharedReaderTextAlign.CENTER -> AndroidReaderTextAlign.LEFT
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
