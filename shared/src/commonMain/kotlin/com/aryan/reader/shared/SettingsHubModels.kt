package com.aryan.reader.shared

enum class SharedSettingsPlatform {
    ANDROID,
    IOS,
    DESKTOP
}

/**
 * Settings copy is declared as a [SharedText] key + English fallback so every host translates it
 * through its own resource catalog. Keys are stable identifiers: changing the English wording never
 * drops a translation.
 */
private fun t(key: String, fallback: String): SharedText =
    SharedText(name = "settings_hub_$key", fallback = fallback)

/**
 * Where the settings hub can navigate.
 *
 * The root list is derived from the category specs, so order, title, and copy have exactly one
 * source of truth.
 */
enum class SharedSettingsDestination {
    ROOT,
    EPUB_TEXT,
    PDF_COMICS,
    APP_LIBRARY,
    TTS_AI,
    SYNC_ACCOUNT,
    STORAGE_PRIVACY,
    HELP_ABOUT,
    EPUB_FORMAT,
    EPUB_THEME_TEXTURE,
    EPUB_VISUAL_DEFAULTS,
    PDF_APPEARANCE_DEFAULTS,
    READER_TOOLBAR_DEFAULTS,
    GLOBAL_TTS_REPLACEMENTS
}

fun SharedSettingsDestination.parentDestination(): SharedSettingsDestination? {
    return when (this) {
        SharedSettingsDestination.ROOT -> null
        SharedSettingsDestination.EPUB_TEXT,
        SharedSettingsDestination.PDF_COMICS,
        SharedSettingsDestination.APP_LIBRARY,
        SharedSettingsDestination.TTS_AI,
        SharedSettingsDestination.SYNC_ACCOUNT,
        SharedSettingsDestination.STORAGE_PRIVACY,
        SharedSettingsDestination.HELP_ABOUT -> SharedSettingsDestination.ROOT
        SharedSettingsDestination.EPUB_FORMAT,
        SharedSettingsDestination.EPUB_THEME_TEXTURE,
        SharedSettingsDestination.EPUB_VISUAL_DEFAULTS,
        SharedSettingsDestination.READER_TOOLBAR_DEFAULTS -> SharedSettingsDestination.EPUB_TEXT
        SharedSettingsDestination.PDF_APPEARANCE_DEFAULTS -> SharedSettingsDestination.PDF_COMICS
        SharedSettingsDestination.GLOBAL_TTS_REPLACEMENTS -> SharedSettingsDestination.TTS_AI
    }
}

enum class SharedSettingsAction {
    TEXT_READER_DEFAULTS,
    PDF_READER_DEFAULTS,
    READER_TOOLBAR,
    TTS_REPLACEMENTS,
    LOCAL_OVERRIDE_NOTE,
    APP_THEME,
    LANGUAGE,
    TABS_TOGGLE,
    STRICT_FILE_FILTER,
    PDF_FILENAME_DISPLAY_NAME,
    EXTERNAL_FILE_BEHAVIOR,
    SCREEN_CAPTURE_PROTECTION,
    CUSTOM_FONTS,
    SIGN_IN,
    SIGN_OUT,
    DELETE_ACCOUNT,
    CLOUD_SYNC,
    FOLDER_SYNC,
    DEVICE_MANAGEMENT,
    AI_SETTINGS,
    HIDE_READER_AI,
    TTS_SETTINGS,
    CLEAR_BOOK_CACHE,
    CLEAR_REFLOW_CACHE,
    CLEAR_CLOUD_LOCAL_DATA,
    TEST_PANEL_DETECTION,
    TEST_SPEECH_BUBBLE_DETECTION,
    EXPORT_LOGS,
    FPS_OVERLAY,
    HELP_FEEDBACK,
    SUPPORT,
    ABOUT
}

enum class SharedSettingsPageKind {
    ROOT,
    CATEGORY,
    DETAIL
}

/**
 * How a row behaves. The renderer derives the affordance from this, so presentation cannot drift
 * from behaviour:
 *
 * - [NAVIGATION] pushes another settings screen: trailing chevron.
 * - [CONTROL] opens a dialog, sheet, or platform flow: no chevron, because nothing is pushed.
 * - [TOGGLE] flips immediately: trailing switch, and the row itself is the switch target.
 * - [DESTRUCTIVE] removes or disconnects something: moved into a trailing group, error label, no
 *   chevron, confirmed by the host.
 * - [INFO] is a static note: not clickable at all.
 */
enum class SharedSettingsItemKind {
    NAVIGATION,
    CONTROL,
    TOGGLE,
    DESTRUCTIVE,
    INFO
}

data class SharedSettingsCategoryModel(
    val destination: SharedSettingsDestination,
    val title: SharedText,
    val summary: SharedText
) {
    fun matches(query: String): Boolean {
        val normalized = query.trim()
        if (normalized.isBlank()) return true
        return title.fallback.contains(normalized, ignoreCase = true) ||
            summary.fallback.contains(normalized, ignoreCase = true) ||
            destination.name.contains(normalized, ignoreCase = true)
    }
}

data class SharedSettingsItemGroup(
    val header: SharedText? = null,
    val items: List<SharedSettingsItemModel> = emptyList()
)

data class SharedSettingsItemModel(
    /**
     * Stable row identity. A row that navigates uses its destination name, every other row uses its
     * action name. Several rows can share one action (the EPUB rows default to
     * [SharedSettingsAction.TEXT_READER_DEFAULTS] and differ by destination), so identity cannot be
     * the action.
     */
    val id: String,
    val action: SharedSettingsAction,
    val title: SharedText,
    val summary: SharedText,
    val kind: SharedSettingsItemKind = SharedSettingsItemKind.NAVIGATION,
    val enabled: Boolean = true,
    val checked: Boolean? = null,
    val destination: SharedSettingsDestination? = null,
    /** Current value preview supplied by the host, e.g. "Literata · 100% · Justified". */
    val value: String? = null,
    /** Extra search terms so a row is findable by the control names it leads to. */
    val keywords: List<String> = emptyList()
) {
    fun matches(query: String): Boolean {
        val normalized = query.trim()
        if (normalized.isBlank()) return true
        return title.fallback.contains(normalized, ignoreCase = true) ||
            summary.fallback.contains(normalized, ignoreCase = true) ||
            id.contains(normalized, ignoreCase = true) ||
            action.name.contains(normalized, ignoreCase = true) ||
            keywords.any { it.contains(normalized, ignoreCase = true) }
    }
}

data class SharedSettingsPageModel(
    val destination: SharedSettingsDestination,
    val title: SharedText,
    val summary: SharedText,
    val kind: SharedSettingsPageKind,
    val parent: SharedSettingsDestination?,
    val categories: List<SharedSettingsCategoryModel> = emptyList(),
    val groups: List<SharedSettingsItemGroup> = emptyList(),
    val localOverrideNote: SharedSettingsItemModel? = null
) {
    val items: List<SharedSettingsItemModel> get() = groups.flatMap { it.items }

    fun item(action: SharedSettingsAction): SharedSettingsItemModel? =
        items.firstOrNull { it.action == action }

    fun itemById(id: String): SharedSettingsItemModel? = items.firstOrNull { it.id == id }
}

/**
 * A search hit. Results always navigate: the hub never runs a setting's action from a result row,
 * because a global result list is not the place to silently toggle or delete anything.
 */
data class SharedSettingsSearchResult(
    val title: SharedText,
    val summary: SharedText,
    val breadcrumb: List<SharedText>,
    val destination: SharedSettingsDestination,
    val kind: SharedSettingsItemKind = SharedSettingsItemKind.NAVIGATION,
    val enabled: Boolean = true
)

class SharedSettingsHubModel internal constructor(
    val platform: SharedSettingsPlatform,
    private val specs: List<SharedSettingsCategorySpec>
) {
    val rootCategories: List<SharedSettingsCategoryModel> = specs
        .filter { spec -> spec.groups.any { it.items.isNotEmpty() } }
        .map { spec ->
            SharedSettingsCategoryModel(
                destination = spec.destination,
                title = spec.title,
                summary = spec.summary
            )
        }

    fun page(destination: SharedSettingsDestination): SharedSettingsPageModel {
        if (destination == SharedSettingsDestination.ROOT) {
            return SharedSettingsPageModel(
                destination = SharedSettingsDestination.ROOT,
                title = t("root_title", "Settings"),
                summary = t("root_summary", "Reading, app, account, and device preferences"),
                kind = SharedSettingsPageKind.ROOT,
                parent = null,
                categories = rootCategories
            )
        }

        val spec = specs.firstOrNull { it.destination == destination }
            ?: return detailPage(destination)

        return SharedSettingsPageModel(
            destination = spec.destination,
            title = spec.title,
            summary = spec.summary,
            kind = SharedSettingsPageKind.CATEGORY,
            parent = spec.destination.parentDestination(),
            groups = spec.groups.filter { it.items.isNotEmpty() },
            localOverrideNote = if (spec.destination.showPerBookNote) PER_BOOK_OVERRIDE_NOTE else null
        )
    }

    fun searchResults(query: String): List<SharedSettingsSearchResult> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()

        val settingsCrumb = t("root_title", "Settings")
        val categoryResults = rootCategories
            .filter { it.matches(normalized) }
            .map { category ->
                SharedSettingsSearchResult(
                    title = category.title,
                    summary = category.summary,
                    breadcrumb = listOf(settingsCrumb),
                    destination = category.destination
                )
            }

        val itemResults = specs.flatMap { spec ->
            spec.groups.flatMap { group ->
                group.items.filter { it.matches(normalized) }.map { item ->
                    SharedSettingsSearchResult(
                        title = item.title,
                        summary = item.summary,
                        breadcrumb = listOf(settingsCrumb, spec.title),
                        // A row that opens a dialog has no destination of its own, so the result
                        // lands on its page instead of running the action from a list.
                        destination = item.destination ?: spec.destination,
                        kind = item.kind,
                        enabled = item.enabled
                    )
                }
            }
        }

        return (categoryResults + itemResults)
            .distinctBy { result ->
                "${result.destination}|${result.title.fallback}|${result.breadcrumb.size}"
            }
    }
}

private fun detailPage(destination: SharedSettingsDestination): SharedSettingsPageModel {
    val (title, summary) = when (destination) {
        SharedSettingsDestination.EPUB_FORMAT -> t("detail_format_title", "Format Defaults") to
            t("detail_format_summary", "Font, size, spacing, margins, alignment, and reading mode")
        SharedSettingsDestination.EPUB_THEME_TEXTURE -> t("detail_theme_title", "EPUB Theme & Texture") to
            t("detail_theme_summary", "Default reading theme, paper texture, and texture strength")
        SharedSettingsDestination.EPUB_VISUAL_DEFAULTS -> t("detail_visual_title", "Visual Defaults") to
            t("detail_visual_summary", "Page indicators, system UI, images, and chapter turns")
        SharedSettingsDestination.PDF_APPEARANCE_DEFAULTS -> t("detail_pdf_title", "PDF Theme Defaults") to
            t(
                "detail_pdf_summary",
                "Default PDF and comic theme where fixed-layout appearance is supported"
            )
        SharedSettingsDestination.READER_TOOLBAR_DEFAULTS -> t("detail_toolbar_title", "Reader Toolbar Defaults") to
            t("detail_toolbar_summary", "Visible tools, bottom-bar actions, and reader overflow tools")
        SharedSettingsDestination.GLOBAL_TTS_REPLACEMENTS -> t("detail_tts_title", "Global TTS Replacements") to
            t("detail_tts_summary", "Words and phrases replaced only during speech playback")
        else -> t("unknown_title", "Settings") to t("unknown_summary", "")
    }
    return SharedSettingsPageModel(
        destination = destination,
        title = title,
        summary = summary,
        kind = SharedSettingsPageKind.DETAIL,
        parent = destination.parentDestination(),
        localOverrideNote = if (destination.showPerBookNote) PER_BOOK_OVERRIDE_NOTE else null
    )
}

/**
 * Everything the hub renders for one category. Built from [SharedSettingsHubInput] once, so the
 * root list, the page, and search can never disagree about what exists.
 */
internal data class SharedSettingsCategorySpec(
    val destination: SharedSettingsDestination,
    val title: SharedText,
    val summary: SharedText,
    val groups: List<SharedSettingsItemGroup>
)

private val SharedSettingsDestination.showPerBookNote: Boolean
    get() = when (this) {
        SharedSettingsDestination.EPUB_FORMAT,
        SharedSettingsDestination.EPUB_THEME_TEXTURE,
        SharedSettingsDestination.EPUB_VISUAL_DEFAULTS,
        SharedSettingsDestination.PDF_APPEARANCE_DEFAULTS -> true
        else -> false
    }

private val PER_BOOK_OVERRIDE_NOTE = SharedSettingsItemModel(
    id = SharedSettingsAction.LOCAL_OVERRIDE_NOTE.name,
    action = SharedSettingsAction.LOCAL_OVERRIDE_NOTE,
    title = t("per_book_title", "Per-book overrides"),
    summary = t(
        "per_book_summary",
        "Defaults apply to new books. Changes made inside the reader still win for that book."
    ),
    kind = SharedSettingsItemKind.INFO
)

data class SharedSettingsHubInput(
    val platform: SharedSettingsPlatform,
    val featurePolicy: SharedFeaturePolicy = SharedFeaturePolicy.Standard,
    val isDebugBuild: Boolean = false,
    val isSignedIn: Boolean = false,
    val isProUser: Boolean = false,
    val accountAvailable: Boolean = true,
    val includeAccountAuthActions: Boolean = true,
    val syncAvailable: Boolean = true,
    /**
     * Optional setup state supplied by platforms that can guide the user to
     * the missing account or Drive authorization step. A null value keeps the
     * legacy platform gate for hosts without that setup flow.
     */
    val cloudSyncSetupIntent: CloudSyncSetupIntent? = null,
    val folderSyncAvailable: Boolean = true,
    val aiSettingsAvailable: Boolean = true,
    val ttsSettingsAvailable: Boolean = true,
    val bookCacheMaintenanceAvailable: Boolean = true,
    val reflowCacheMaintenanceAvailable: Boolean = true,
    val includePdfReaderDefaults: Boolean = true,
    val includeReaderToolbar: Boolean = true,
    val includeLanguage: Boolean = true,
    val includeScreenCaptureProtection: Boolean = false,
    val includeExternalFileBehavior: Boolean = true,
    val includeCustomFonts: Boolean = true,
    val includeStrictFileFilter: Boolean = true,
    val includePdfFileNameDisplayName: Boolean = false,
    val includeReaderTabs: Boolean = true,
    val includeHideReaderAi: Boolean = true,
    val includeCloudLocalDataClear: Boolean = false,
    /**
     * Shows the permanent "Delete account" entry. iOS-only for now: other
     * platforms keep the default until their native re-auth + cleanup exists.
     */
    val includeAccountDeletion: Boolean = false,
    /** Exposes the platform's safe, app-owned recent-log export. */
    val includeDiagnosticLogExport: Boolean = false,
    /** Exposes the debug FPS meter toggle. Both hosts ship a global overlay. */
    val includeFpsOverlayToggle: Boolean = false,
    val fpsOverlayEnabled: Boolean = false,
    val supportProjectAvailable: Boolean = true,
    val isTabsEnabled: Boolean = true,
    val isSyncEnabled: Boolean = false,
    val isFolderSyncEnabled: Boolean = false,
    val useStrictFileFilter: Boolean = false,
    val usePdfFileNameAsDisplayName: Boolean = false,
    val isScreenCaptureProtectionEnabled: Boolean = false,
    val hideReaderAi: Boolean = false,
    /**
     * Current-value previews keyed by row id (see [SharedSettingsItemModel.id]), supplied by the
     * host because only the host knows its persisted state. Missing entries render as a plain row.
     */
    val itemValues: Map<String, String> = emptyMap()
)

fun sharedSettingsHubModel(input: SharedSettingsHubInput): SharedSettingsHubModel {
    val specs = listOf(
        input.epubTextCategory(),
        input.pdfComicsCategory(),
        input.appLibraryCategory(),
        input.speechAndAiCategory(),
        input.syncAndAccountCategory(),
        input.storageAndPrivacyCategory(),
        input.helpAndAboutCategory()
    )
    return SharedSettingsHubModel(platform = input.platform, specs = specs)
}

private fun SharedSettingsHubInput.item(
    action: SharedSettingsAction,
    title: SharedText,
    summary: SharedText,
    kind: SharedSettingsItemKind = SharedSettingsItemKind.NAVIGATION,
    enabled: Boolean = true,
    checked: Boolean? = null,
    destination: SharedSettingsDestination? = null,
    keywords: List<String> = emptyList()
): SharedSettingsItemModel {
    val id = destination?.name ?: action.name
    return SharedSettingsItemModel(
        id = id,
        action = action,
        title = title,
        summary = summary,
        kind = kind,
        enabled = enabled,
        checked = checked,
        destination = destination,
        value = itemValues[id]?.takeIf { it.isNotBlank() },
        keywords = keywords
    )
}

private fun SharedSettingsHubInput.epubTextCategory(): SharedSettingsCategorySpec {
    val textRows = buildList {
        add(
            item(
                action = SharedSettingsAction.TEXT_READER_DEFAULTS,
                title = t("epub_format_title", "Text and layout"),
                summary = t("epub_format_summary", "Font, size, spacing, margins, alignment, and reading mode"),
                destination = SharedSettingsDestination.EPUB_FORMAT,
                keywords = listOf("font", "font size", "line spacing", "margin", "alignment", "justify", "indent")
            )
        )
        add(
            item(
                action = SharedSettingsAction.TEXT_READER_DEFAULTS,
                title = t("epub_theme_title", "Theme and texture"),
                summary = t("epub_theme_summary", "Reading theme, paper texture, and texture strength"),
                destination = SharedSettingsDestination.EPUB_THEME_TEXTURE,
                keywords = listOf("theme", "texture", "sepia", "dark", "paper")
            )
        )
        add(
            item(
                action = SharedSettingsAction.TEXT_READER_DEFAULTS,
                title = t("epub_visual_title", "Visual behavior"),
                summary = t("epub_visual_summary", "System UI, page indicators, images, and chapter turns"),
                destination = SharedSettingsDestination.EPUB_VISUAL_DEFAULTS,
                keywords = listOf("page number", "status bar", "images", "pull to turn")
            )
        )
    }
    val toolRows = buildList {
        if (includeReaderToolbar) {
            add(
                item(
                    action = SharedSettingsAction.READER_TOOLBAR,
                    title = t("reader_toolbar_title", "Reader toolbar"),
                    summary = t("reader_toolbar_summary", "Visible tools, bottom-bar actions, and overflow tools"),
                    destination = SharedSettingsDestination.READER_TOOLBAR_DEFAULTS,
                    keywords = listOf("toolbar", "tools", "bottom bar", "overflow")
                )
            )
        }
    }
    return SharedSettingsCategorySpec(
        destination = SharedSettingsDestination.EPUB_TEXT,
        title = t("epub_text_title", "EPUB & Text"),
        summary = t("epub_text_summary", "Default text layout, EPUB appearance, and reader tools"),
        groups = listOf(
            SharedSettingsItemGroup(header = t("section_text", "Text"), items = textRows),
            SharedSettingsItemGroup(header = t("section_reader_tools", "Reader tools"), items = toolRows),
            SharedSettingsItemGroup(
                header = t("section_in_reader", "In the reader"),
                items = listOf(
                    item(
                        action = SharedSettingsAction.LOCAL_OVERRIDE_NOTE,
                        title = t("epub_reader_note_title", "Per-book overrides"),
                        summary = t(
                            "epub_reader_note_summary",
                            "Font, theme, and layout changes made inside the reader apply to that book only."
                        ),
                        kind = SharedSettingsItemKind.INFO,
                        keywords = listOf("per book", "override", "reader only")
                    )
                )
            )
        )
    )
}

private fun SharedSettingsHubInput.pdfComicsCategory(): SharedSettingsCategorySpec {
    val appearanceRows = buildList {
        if (includePdfReaderDefaults) {
            add(
                item(
                    action = SharedSettingsAction.PDF_READER_DEFAULTS,
                    title = t("pdf_appearance_title", "Theme and appearance"),
                    summary = t("pdf_appearance_summary", "PDF and comic theme, page gap, and page-number overlay"),
                    destination = SharedSettingsDestination.PDF_APPEARANCE_DEFAULTS,
                    keywords = listOf("pdf theme", "page gap", "page number", "right to left")
                )
            )
        }
    }
    val noteRows = buildList {
        if (includePdfReaderDefaults) {
            add(
                item(
                    action = SharedSettingsAction.PDF_READER_DEFAULTS,
                    title = t("pdf_tools_title", "PDF reader tools"),
                    summary = t(
                        "pdf_tools_summary",
                        "Auto-scroll, OCR, annotations, and PDF-only tools stay inside the PDF reader."
                    ),
                    kind = SharedSettingsItemKind.INFO,
                    keywords = listOf("ocr", "auto scroll", "annotation", "panel detection")
                )
            )
        }
    }
    return SharedSettingsCategorySpec(
        destination = SharedSettingsDestination.PDF_COMICS,
        title = t("pdf_comics_title", "PDF & Comics"),
        summary = t("pdf_comics_summary", "Fixed-layout appearance and reader-managed PDF tools"),
        groups = listOf(
            SharedSettingsItemGroup(header = t("section_appearance", "Appearance"), items = appearanceRows),
            SharedSettingsItemGroup(header = t("section_in_reader", "In the reader"), items = noteRows)
        )
    )
}

private fun SharedSettingsHubInput.appLibraryCategory(): SharedSettingsCategorySpec {
    val appearanceRows = buildList {
        add(
            item(
                action = SharedSettingsAction.APP_THEME,
                title = t("app_theme_title", "App theme"),
                summary = t("app_theme_summary", "Theme mode, contrast, text dimming, and custom colors"),
                kind = SharedSettingsItemKind.CONTROL,
                keywords = listOf("dark mode", "amoled", "contrast", "seed color", "dynamic color")
            )
        )
    }
    val libraryRows = buildList {
        if (includeLanguage) {
            add(
                item(
                    action = SharedSettingsAction.LANGUAGE,
                    title = t("language_title", "Language"),
                    summary = t("language_summary", "Choose the app language"),
                    kind = SharedSettingsItemKind.CONTROL,
                    keywords = listOf("locale", "translation", "english")
                )
            )
        }
        if (includeCustomFonts) {
            add(
                item(
                    action = SharedSettingsAction.CUSTOM_FONTS,
                    title = t("custom_fonts_title", "Custom fonts"),
                    summary = t("custom_fonts_summary", "Import, manage, and reuse reading fonts"),
                    keywords = listOf("font", "typeface", "import")
                )
            )
        }
        if (includeReaderTabs) {
            add(
                item(
                    action = SharedSettingsAction.TABS_TOGGLE,
                    title = t("tabs_title", "Reader tabs"),
                    summary = t("tabs_summary", "Keep multiple PDFs open instead of replacing the reader"),
                    kind = SharedSettingsItemKind.TOGGLE,
                    checked = isTabsEnabled,
                    keywords = listOf("tabs", "sessions")
                )
            )
        }
        if (includeExternalFileBehavior) {
            add(
                item(
                    action = SharedSettingsAction.EXTERNAL_FILE_BEHAVIOR,
                    title = t("external_file_title", "External file behavior"),
                    summary = t("external_file_summary", "Whether files opened from other apps are copied into the library"),
                    kind = SharedSettingsItemKind.CONTROL,
                    keywords = listOf("open with", "import", "copy")
                )
            )
        }
        if (includeStrictFileFilter) {
            add(
                item(
                    action = SharedSettingsAction.STRICT_FILE_FILTER,
                    title = t("strict_filter_title", "Strict file filter"),
                    summary = t("strict_filter_summary", "Limit import pickers to known reader file types"),
                    kind = SharedSettingsItemKind.TOGGLE,
                    checked = useStrictFileFilter,
                    keywords = listOf("import", "file types", "extensions")
                )
            )
        }
        if (includePdfFileNameDisplayName) {
            add(
                item(
                    action = SharedSettingsAction.PDF_FILENAME_DISPLAY_NAME,
                    title = t("pdf_filenames_title", "Use PDF filenames"),
                    summary = t("pdf_filenames_summary", "Show filenames instead of embedded PDF titles"),
                    kind = SharedSettingsItemKind.TOGGLE,
                    checked = usePdfFileNameAsDisplayName,
                    keywords = listOf("title", "metadata", "filename")
                )
            )
        }
    }
    return SharedSettingsCategorySpec(
        destination = SharedSettingsDestination.APP_LIBRARY,
        title = t("app_library_title", "App & Library"),
        summary = t("app_library_summary", "Appearance, language, and how the library handles files"),
        groups = listOf(
            SharedSettingsItemGroup(header = t("section_appearance", "Appearance"), items = appearanceRows),
            SharedSettingsItemGroup(header = t("section_library_files", "Library & files"), items = libraryRows)
        )
    )
}

private fun SharedSettingsHubInput.speechAndAiCategory(): SharedSettingsCategorySpec {
    val speechRows = buildList {
        if (ttsSettingsAvailable) {
            add(
                item(
                    action = SharedSettingsAction.TTS_SETTINGS,
                    title = t("tts_title", "Voice and speech"),
                    summary = if (platform == SharedSettingsPlatform.IOS) {
                        t("tts_summary_ios", "Choose an iOS voice, speech rate, and pitch")
                    } else {
                        t("tts_summary_android", "Choose cloud or device voices and speech behavior")
                    },
                    kind = SharedSettingsItemKind.CONTROL,
                    keywords = listOf("tts", "voice", "speech rate", "pitch", "read aloud")
                )
            )
        }
        add(
            item(
                action = SharedSettingsAction.TTS_REPLACEMENTS,
                title = t("tts_replacements_title", "Speech replacements"),
                summary = t("tts_replacements_summary", "Words and phrases replaced only during speech playback"),
                destination = SharedSettingsDestination.GLOBAL_TTS_REPLACEMENTS,
                keywords = listOf("pronunciation", "replace", "tts dictionary")
            )
        )
    }
    val aiRows = buildList {
        if (aiSettingsAvailable && featurePolicy.aiAndCloud) {
            add(
                item(
                    action = SharedSettingsAction.AI_SETTINGS,
                    title = t("ai_settings_title", "AI keys and models"),
                    summary = t("ai_settings_summary", "Configure reader AI and cloud TTS model access"),
                    keywords = listOf("api key", "model", "gemini", "openai", "cloud tts")
                )
            )
        }
        if (includeHideReaderAi && featurePolicy.aiAndCloud) {
            add(
                item(
                    action = SharedSettingsAction.HIDE_READER_AI,
                    title = t("show_reader_ai_title", "Show reader AI"),
                    summary = t("show_reader_ai_summary", "Reader AI tools appear where the book supports them"),
                    kind = SharedSettingsItemKind.TOGGLE,
                    checked = !hideReaderAi,
                    keywords = listOf("ai", "assistant", "hide")
                )
            )
        }
    }
    return SharedSettingsCategorySpec(
        destination = SharedSettingsDestination.TTS_AI,
        title = if (aiRows.isNotEmpty()) {
            t("speech_ai_title", "Speech & AI")
        } else {
            t("speech_title", "Speech")
        },
        summary = if (aiRows.isNotEmpty()) {
            t("speech_ai_summary", "Voice, speech replacements, and reader AI")
        } else {
            t("speech_summary", "Voice, speech behavior, and replacements")
        },
        groups = listOf(
            SharedSettingsItemGroup(header = t("section_speech", "Speech"), items = speechRows),
            SharedSettingsItemGroup(header = t("section_reader_ai", "Reader AI"), items = aiRows)
        )
    )
}

private fun SharedSettingsHubInput.syncAndAccountCategory(): SharedSettingsCategorySpec {
    val syncRows = buildList {
        if (syncAvailable && featurePolicy.aiAndCloud) {
            val setupIntent = cloudSyncSetupIntent
            // Entitlement never disables the row: a disabled switch with no action is a dead end.
            // Tapping always reaches the host, which either toggles sync or opens the upgrade flow.
            add(
                item(
                    action = SharedSettingsAction.CLOUD_SYNC,
                    title = t("cloud_sync_title", "Cloud library sync"),
                    summary = when (setupIntent) {
                        CloudSyncSetupIntent.NEEDS_PRO,
                        null -> if (!isProUser) {
                            t("cloud_sync_pro_required", "Included with Pro. Tap to see plans.")
                        } else {
                            t("cloud_sync_ready", "Sync library metadata across signed-in devices.")
                        }
                        CloudSyncSetupIntent.NEEDS_GOOGLE_LINK ->
                            t("cloud_sync_link_google", "Link Google to enable cloud sync.")
                        CloudSyncSetupIntent.NEEDS_DRIVE_AUTH ->
                            t("cloud_sync_drive_auth", "Authorize Google Drive to enable sync.")
                        CloudSyncSetupIntent.READY ->
                            t("cloud_sync_ready", "Sync library metadata across signed-in devices.")
                    },
                    kind = SharedSettingsItemKind.TOGGLE,
                    enabled = true,
                    checked = isSyncEnabled,
                    keywords = listOf("cloud", "drive", "backup", "sync")
                )
            )
        }
        if (folderSyncAvailable) {
            add(
                item(
                    action = SharedSettingsAction.FOLDER_SYNC,
                    title = t("folder_sync_title", "Folder backup and sync"),
                    summary = t("folder_sync_summary", "Choose which local folders back up to Drive"),
                    kind = SharedSettingsItemKind.CONTROL,
                    keywords = listOf("folder", "drive", "backup")
                )
            )
        }
    }
    val accountRows = buildList {
        if (includeAccountAuthActions && accountAvailable && featurePolicy.aiAndCloud) {
            if (isSignedIn) {
                add(
                    item(
                        action = SharedSettingsAction.SIGN_OUT,
                        title = t("sign_out_title", "Sign out"),
                        summary = t("sign_out_summary", "Disconnect this device from your account"),
                        kind = SharedSettingsItemKind.DESTRUCTIVE,
                        keywords = listOf("logout", "account")
                    )
                )
            } else {
                add(
                    item(
                        action = SharedSettingsAction.SIGN_IN,
                        title = t("sign_in_title", "Sign in"),
                        summary = t("sign_in_summary", "Connect sync and account features"),
                        kind = SharedSettingsItemKind.CONTROL,
                        keywords = listOf("login", "account", "google")
                    )
                )
            }
        }
        if (isDebugBuild && featurePolicy.aiAndCloud && syncAvailable) {
            add(
                item(
                    action = SharedSettingsAction.DEVICE_MANAGEMENT,
                    title = t("devices_title", "Device management"),
                    summary = t("devices_summary", "Inspect registered devices for this account"),
                    keywords = listOf("devices", "sessions", "login")
                )
            )
        }
        // iOS-only for now: Android keeps no deletion entry until its native re-auth + backend
        // cleanup path is built to match.
        if (includeAccountDeletion && isSignedIn && accountAvailable && featurePolicy.aiAndCloud) {
            add(
                item(
                    action = SharedSettingsAction.DELETE_ACCOUNT,
                    title = t("delete_account_title", "Delete account"),
                    summary = t(
                        "delete_account_summary",
                        "Permanently delete your account and cloud data. Purchases are not refunded and credits are lost."
                    ),
                    kind = SharedSettingsItemKind.DESTRUCTIVE,
                    keywords = listOf("delete", "remove", "account")
                )
            )
        }
    }
    return SharedSettingsCategorySpec(
        destination = SharedSettingsDestination.SYNC_ACCOUNT,
        title = t("sync_account_title", "Sync & Account"),
        summary = t("sync_account_summary", "Cloud sync, folder backup, and account management"),
        groups = listOf(
            SharedSettingsItemGroup(header = t("section_sync", "Sync"), items = syncRows),
            SharedSettingsItemGroup(header = t("section_account", "Account"), items = accountRows)
        )
    )
}

private fun SharedSettingsHubInput.storageAndPrivacyCategory(): SharedSettingsCategorySpec {
    val maintenanceRows = buildList {
        if (bookCacheMaintenanceAvailable) {
            add(
                item(
                    action = SharedSettingsAction.CLEAR_BOOK_CACHE,
                    title = t("clear_book_cache_title", "Clear book cache"),
                    summary = t(
                        "clear_book_cache_summary",
                        "Remove generated book cache files and recreate them on demand"
                    ),
                    kind = SharedSettingsItemKind.DESTRUCTIVE,
                    keywords = listOf("cache", "storage", "clean")
                )
            )
        }
        if (reflowCacheMaintenanceAvailable) {
            add(
                item(
                    action = SharedSettingsAction.CLEAR_REFLOW_CACHE,
                    title = t("clear_reflow_cache_title", "Clear reflow cache"),
                    summary = t("clear_reflow_cache_summary", "Remove generated PDF text-view files"),
                    kind = SharedSettingsItemKind.DESTRUCTIVE,
                    keywords = listOf("cache", "pdf", "reflow")
                )
            )
        }
        if (isDebugBuild && includeCloudLocalDataClear && featurePolicy.aiAndCloud) {
            add(
                item(
                    action = SharedSettingsAction.CLEAR_CLOUD_LOCAL_DATA,
                    title = t("clear_cloud_data_title", "Clear cloud and local data"),
                    summary = t("clear_cloud_data_summary", "Delete cloud records and matching local library data"),
                    kind = SharedSettingsItemKind.DESTRUCTIVE,
                    keywords = listOf("reset", "delete", "cloud")
                )
            )
        }
    }
    val privacyRows = buildList {
        if (includeScreenCaptureProtection) {
            add(
                item(
                    action = SharedSettingsAction.SCREEN_CAPTURE_PROTECTION,
                    title = t("screen_capture_title", "Screen capture protection"),
                    summary = t("screen_capture_summary", "Block screenshots and screen recording on reader screens"),
                    kind = SharedSettingsItemKind.TOGGLE,
                    checked = isScreenCaptureProtectionEnabled,
                    keywords = listOf("screenshot", "privacy", "recording")
                )
            )
        }
    }
    val diagnosticsRows = buildList {
        if (isDebugBuild || includeDiagnosticLogExport) {
            add(
                item(
                    action = SharedSettingsAction.EXPORT_LOGS,
                    title = t("export_logs_title", "Export logs"),
                    summary = t("export_logs_summary", "Share recent diagnostic logs collected by the app"),
                    kind = SharedSettingsItemKind.CONTROL,
                    keywords = listOf("logs", "diagnostics", "support")
                )
            )
        }
    }
    return SharedSettingsCategorySpec(
        destination = SharedSettingsDestination.STORAGE_PRIVACY,
        title = t("storage_privacy_title", "Storage & Privacy"),
        summary = t("storage_privacy_summary", "Cache maintenance, privacy, and diagnostics"),
        groups = listOf(
            SharedSettingsItemGroup(header = t("section_storage", "Storage"), items = maintenanceRows),
            SharedSettingsItemGroup(header = t("section_privacy", "Privacy"), items = privacyRows),
            SharedSettingsItemGroup(header = t("section_diagnostics", "Diagnostics"), items = diagnosticsRows)
        )
    )
}

private fun SharedSettingsHubInput.helpAndAboutCategory(): SharedSettingsCategorySpec {
    val helpRows = buildList {
        if (featurePolicy.projectLinks) {
            add(
                item(
                    action = SharedSettingsAction.HELP_FEEDBACK,
                    title = t("help_feedback_title", "Help and feedback"),
                    summary = t("help_feedback_summary", "Send feedback or report an issue"),
                    keywords = listOf("feedback", "bug", "issue")
                )
            )
            if (supportProjectAvailable) {
                add(
                    item(
                        action = SharedSettingsAction.SUPPORT,
                        title = t("support_title", "Support the project"),
                        summary = t("support_summary", "Open support options for the project"),
                        keywords = listOf("donate", "support")
                    )
                )
            }
        }
        add(
            item(
                action = SharedSettingsAction.ABOUT,
                title = t("about_title", "About"),
                summary = t("about_summary", "Version, source, licenses, and project information"),
                keywords = listOf("version", "licenses", "open source")
            )
        )
    }
    val developerRows = buildList {
        if (isDebugBuild) {
            if (platform == SharedSettingsPlatform.ANDROID) {
                add(
                    item(
                        action = SharedSettingsAction.TEST_PANEL_DETECTION,
                        title = t("test_panel_title", "Test panel detection"),
                        summary = t("test_panel_summary", "Run the local panel-detection diagnostic"),
                        kind = SharedSettingsItemKind.CONTROL
                    )
                )
                add(
                    item(
                        action = SharedSettingsAction.TEST_SPEECH_BUBBLE_DETECTION,
                        title = t("test_speech_bubble_title", "Test speech-bubble detection"),
                        summary = t("test_speech_bubble_summary", "Run the local speech-bubble detection diagnostic"),
                        kind = SharedSettingsItemKind.CONTROL
                    )
                )
            }
            if (includeFpsOverlayToggle) {
                add(
                    item(
                        action = SharedSettingsAction.FPS_OVERLAY,
                        title = t("fps_overlay_title", "Show FPS overlay"),
                        summary = t("fps_overlay_summary", "Draw a frame-rate meter over the app"),
                        kind = SharedSettingsItemKind.TOGGLE,
                        checked = fpsOverlayEnabled
                    )
                )
            }
        }
    }
    return SharedSettingsCategorySpec(
        destination = SharedSettingsDestination.HELP_ABOUT,
        title = t("help_about_title", "Help & About"),
        summary = t("help_about_summary", "Feedback, support, and app information"),
        groups = listOf(
            SharedSettingsItemGroup(items = helpRows),
            SharedSettingsItemGroup(header = t("section_developer", "Developer"), items = developerRows)
        )
    )
}
