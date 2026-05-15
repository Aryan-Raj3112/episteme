package com.aryan.reader.shared

enum class SharedSettingsPlatform {
    ANDROID,
    DESKTOP
}

enum class SharedSettingsSection(
    val title: String,
    val summary: String
) {
    READER(
        title = "Reader settings",
        summary = "Global defaults for text, EPUB, PDF, toolbar, and speech"
    ),
    APP_LIBRARY(
        title = "App & library",
        summary = "App preferences, imports, tabs, and local library behavior"
    ),
    SYNC_ACCOUNTS(
        title = "Sync & accounts",
        summary = "Sign-in, cloud sync, and folder backup"
    ),
    AI_TTS(
        title = "AI & TTS",
        summary = "Reader AI, keys, models, voices, and speech preferences"
    ),
    STORAGE_ADVANCED(
        title = "Storage & advanced",
        summary = "Caches and diagnostic tools"
    ),
    HELP(
        title = "Help",
        summary = "Feedback, support, and app information"
    ),
    EXTRA(
        title = "Extra",
        summary = "Overflow options, maintenance, and diagnostics"
    )
}

enum class SharedSettingsDestination {
    ROOT,
    EPUB_TEXT,
    PDF_COMICS,
    THEME_APPEARANCE,
    TTS_AI,
    LIBRARY_SYNC_STORAGE,
    SYNC_ACCOUNTS,
    EXTRA,
    HELP_ABOUT,
    EPUB_FORMAT,
    EPUB_THEME_TEXTURE,
    EPUB_VISUAL_DEFAULTS,
    PDF_APPEARANCE_DEFAULTS,
    PDF_READER_TOOLS,
    READER_TOOLBAR_DEFAULTS,
    EPUB_TTS_REPLACEMENTS,
    GLOBAL_TTS_REPLACEMENTS
}

fun SharedSettingsDestination.parentDestination(): SharedSettingsDestination? {
    return when (this) {
        SharedSettingsDestination.ROOT -> null
        SharedSettingsDestination.EPUB_TEXT,
        SharedSettingsDestination.PDF_COMICS,
        SharedSettingsDestination.THEME_APPEARANCE,
        SharedSettingsDestination.TTS_AI,
        SharedSettingsDestination.LIBRARY_SYNC_STORAGE,
        SharedSettingsDestination.SYNC_ACCOUNTS,
        SharedSettingsDestination.EXTRA,
        SharedSettingsDestination.HELP_ABOUT -> SharedSettingsDestination.ROOT
        SharedSettingsDestination.EPUB_FORMAT,
        SharedSettingsDestination.EPUB_THEME_TEXTURE,
        SharedSettingsDestination.EPUB_VISUAL_DEFAULTS,
        SharedSettingsDestination.READER_TOOLBAR_DEFAULTS,
        SharedSettingsDestination.EPUB_TTS_REPLACEMENTS -> SharedSettingsDestination.EPUB_TEXT
        SharedSettingsDestination.PDF_APPEARANCE_DEFAULTS,
        SharedSettingsDestination.PDF_READER_TOOLS -> SharedSettingsDestination.PDF_COMICS
        SharedSettingsDestination.GLOBAL_TTS_REPLACEMENTS -> SharedSettingsDestination.TTS_AI
    }
}

enum class SharedSettingsPageKind {
    ROOT,
    CATEGORY,
    DETAIL
}

data class SharedSettingsCategoryModel(
    val destination: SharedSettingsDestination,
    val title: String,
    val summary: String,
    val itemCount: Int
) {
    fun matches(query: String): Boolean {
        val normalized = query.trim()
        if (normalized.isBlank()) return true
        return title.contains(normalized, ignoreCase = true) ||
            summary.contains(normalized, ignoreCase = true) ||
            destination.name.contains(normalized, ignoreCase = true)
    }
}

data class SharedSettingsPageModel(
    val destination: SharedSettingsDestination,
    val title: String,
    val summary: String,
    val kind: SharedSettingsPageKind,
    val parent: SharedSettingsDestination?,
    val categories: List<SharedSettingsCategoryModel> = emptyList(),
    val items: List<SharedSettingsItemModel> = emptyList(),
    val localOverrideNote: SharedSettingsItemModel? = null
)

data class SharedSettingsSearchResult(
    val title: String,
    val summary: String,
    val breadcrumb: String,
    val destination: SharedSettingsDestination? = null,
    val action: SharedSettingsAction? = null,
    val kind: SharedSettingsItemKind = SharedSettingsItemKind.NAVIGATION,
    val enabled: Boolean = true,
    val checked: Boolean? = null
)

enum class SharedSettingsItemKind {
    CONTROL,
    NAVIGATION,
    TOGGLE,
    DESTRUCTIVE,
    INFO
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
    RECENT_LIMIT,
    STRICT_FILE_FILTER,
    EXTERNAL_FILE_BEHAVIOR,
    SCREEN_CAPTURE_PROTECTION,
    CUSTOM_FONTS,
    SIGN_IN,
    SIGN_OUT,
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
    DEBUG_ACTIONS,
    HELP_FEEDBACK,
    SUPPORT,
    ABOUT
}

data class SharedSettingsItemModel(
    val action: SharedSettingsAction,
    val title: String,
    val summary: String,
    val kind: SharedSettingsItemKind = SharedSettingsItemKind.NAVIGATION,
    val enabled: Boolean = true,
    val checked: Boolean? = null,
    val destination: SharedSettingsDestination? = null
) {
    fun matches(query: String): Boolean {
        val normalized = query.trim()
        if (normalized.isBlank()) return true
        return title.contains(normalized, ignoreCase = true) ||
            summary.contains(normalized, ignoreCase = true) ||
            action.name.contains(normalized, ignoreCase = true)
    }
}

data class SharedSettingsSectionModel(
    val section: SharedSettingsSection,
    val items: List<SharedSettingsItemModel>
) {
    fun matches(query: String): Boolean {
        val normalized = query.trim()
        if (normalized.isBlank()) return true
        return section.title.contains(normalized, ignoreCase = true) ||
            section.summary.contains(normalized, ignoreCase = true)
    }
}

data class SharedSettingsHubModel(
    val platform: SharedSettingsPlatform,
    val sections: List<SharedSettingsSectionModel>
) {
    val rootCategories: List<SharedSettingsCategoryModel>
        get() = buildRootCategories()

    fun page(destination: SharedSettingsDestination): SharedSettingsPageModel {
        return when (destination) {
            SharedSettingsDestination.ROOT -> SharedSettingsPageModel(
                destination = SharedSettingsDestination.ROOT,
                title = "Settings",
                summary = "Global defaults, app preferences, and advanced options",
                kind = SharedSettingsPageKind.ROOT,
                parent = null,
                categories = rootCategories
            )
            SharedSettingsDestination.EPUB_TEXT -> categoryPage(
                destination = destination,
                title = "EPUB & Text",
                summary = "Defaults for reflowable reading, layout, EPUB themes, and reader tools",
                items = epubAndTextItems()
            )
            SharedSettingsDestination.PDF_COMICS -> categoryPage(
                destination = destination,
                title = "PDF & Comics",
                summary = "Defaults for fixed-layout reading, PDF themes, and PDF-specific tools",
                items = pdfAndComicItems()
            )
            SharedSettingsDestination.THEME_APPEARANCE -> categoryPage(
                destination = destination,
                title = "App Preferences",
                summary = "App theme and general app behavior",
                items = themeAndAppearanceItems()
            )
            SharedSettingsDestination.TTS_AI -> categoryPage(
                destination = destination,
                title = ttsAiTitle(),
                summary = ttsAiSummary(),
                items = ttsAndAiItems()
            )
            SharedSettingsDestination.LIBRARY_SYNC_STORAGE -> categoryPage(
                destination = destination,
                title = "Library & Files",
                summary = "Recent files and local reading fonts",
                items = libraryAndFileItems()
            )
            SharedSettingsDestination.SYNC_ACCOUNTS -> categoryPage(
                destination = destination,
                title = "Sync & Accounts",
                summary = "Sign-in, cloud sync, folder sync, and devices",
                items = syncAndAccountItems()
            )
            SharedSettingsDestination.EXTRA -> categoryPage(
                destination = destination,
                title = "Extra",
                summary = "More-menu options, maintenance actions, diagnostics, and app info",
                items = extraItems()
            )
            SharedSettingsDestination.HELP_ABOUT -> categoryPage(
                destination = destination,
                title = "Help & About",
                summary = "Feedback, support, project information, and licenses",
                items = helpAndAboutItems()
            )
            SharedSettingsDestination.EPUB_FORMAT -> detailPage(
                destination = destination,
                title = "Format Defaults",
                summary = "Font, size, spacing, margins, alignment, and reading mode",
                localOverrideNote = localOverrideItem()
            )
            SharedSettingsDestination.EPUB_THEME_TEXTURE -> detailPage(
                destination = destination,
                title = "EPUB Theme & Texture",
                summary = "Default EPUB reading theme, paper texture, and texture strength",
                localOverrideNote = localOverrideItem()
            )
            SharedSettingsDestination.EPUB_VISUAL_DEFAULTS -> detailPage(
                destination = destination,
                title = "Visual Defaults",
                summary = "Page indicators, system UI, images, and chapter-turn behavior",
                localOverrideNote = localOverrideItem()
            )
            SharedSettingsDestination.PDF_APPEARANCE_DEFAULTS -> detailPage(
                destination = destination,
                title = "PDF Theme Defaults",
                summary = "Default PDF and comic theme where fixed-layout appearance is supported",
                localOverrideNote = localOverrideItem()
            )
            SharedSettingsDestination.PDF_READER_TOOLS -> detailPage(
                destination = destination,
                title = "PDF Reader Tools",
                summary = "Auto-scroll, OCR, annotation, and PDF-only tools remain in the PDF reader",
                localOverrideNote = localOverrideItem()
            )
            SharedSettingsDestination.READER_TOOLBAR_DEFAULTS -> detailPage(
                destination = destination,
                title = "Reader Toolbar Defaults",
                summary = "Visible tools, bottom-bar actions, and reader overflow tools",
                localOverrideNote = localOverrideItem()
            )
            SharedSettingsDestination.EPUB_TTS_REPLACEMENTS -> detailPage(
                destination = destination,
                title = "Global TTS Replacements",
                summary = "Words and phrases replaced only during speech playback",
                localOverrideNote = localOverrideItem()
            )
            SharedSettingsDestination.GLOBAL_TTS_REPLACEMENTS -> detailPage(
                destination = destination,
                title = "Global TTS Replacements",
                summary = "Words and phrases replaced only during speech playback",
                localOverrideNote = localOverrideItem()
            )
        }
    }

    fun searchResults(query: String): List<SharedSettingsSearchResult> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()

        val categoryResults = rootCategories
            .filter { it.matches(normalized) }
            .map { category ->
                SharedSettingsSearchResult(
                    title = category.title,
                    summary = category.summary,
                    breadcrumb = "Settings",
                    destination = category.destination
                )
            }

        val itemResults = listOf(
            SharedSettingsDestination.EPUB_TEXT,
            SharedSettingsDestination.PDF_COMICS,
            SharedSettingsDestination.THEME_APPEARANCE,
            SharedSettingsDestination.TTS_AI,
            SharedSettingsDestination.LIBRARY_SYNC_STORAGE,
            SharedSettingsDestination.SYNC_ACCOUNTS,
            SharedSettingsDestination.EXTRA
        ).flatMap { destination ->
            val page = page(destination)
            page.items
                .filter { it.matches(normalized) }
                .map { item ->
                    SharedSettingsSearchResult(
                        title = item.title,
                        summary = item.summary,
                        breadcrumb = "Settings / ${page.title}",
                        destination = item.destination,
                        action = item.action,
                        kind = item.kind,
                        enabled = item.enabled,
                        checked = item.checked
                    )
                }
        }

        return (categoryResults + itemResults)
            .distinctBy { result -> result.searchIdentity() }
    }

    fun filtered(query: String): SharedSettingsHubModel {
        val normalized = query.trim()
        if (normalized.isBlank()) return this
        return copy(
            sections = sections.mapNotNull { section ->
                val matchingItems = section.items.filter { it.matches(normalized) }
                when {
                    matchingItems.isNotEmpty() -> section.copy(items = matchingItems)
                    section.matches(normalized) -> section
                    else -> null
                }
            }
        )
    }

    fun itemsIn(section: SharedSettingsSection): List<SharedSettingsItemModel> {
        return sections.firstOrNull { it.section == section }?.items.orEmpty()
    }

    private fun buildRootCategories(): List<SharedSettingsCategoryModel> {
        return listOf(
            rootCategory(
                destination = SharedSettingsDestination.EPUB_TEXT,
                title = "EPUB & Text",
                summary = "Format, EPUB theme, visual defaults, and reader tools",
                itemCount = epubAndTextItems().size
            ),
            rootCategory(
                destination = SharedSettingsDestination.PDF_COMICS,
                title = "PDF & Comics",
                summary = "Separate PDF theme and fixed-layout reader defaults",
                itemCount = pdfAndComicItems().size
            ),
            rootCategory(
                destination = SharedSettingsDestination.THEME_APPEARANCE,
                title = "App Preferences",
                summary = "App theme and general app behavior",
                itemCount = themeAndAppearanceItems().size
            ),
            rootCategory(
                destination = SharedSettingsDestination.TTS_AI,
                title = ttsAiTitle(),
                summary = ttsAiSummary(),
                itemCount = ttsAndAiItems().size
            ),
            rootCategory(
                destination = SharedSettingsDestination.LIBRARY_SYNC_STORAGE,
                title = "Library & Files",
                summary = "Recent files and local reading fonts",
                itemCount = libraryAndFileItems().size
            ),
            rootCategory(
                destination = SharedSettingsDestination.SYNC_ACCOUNTS,
                title = "Sync & Accounts",
                summary = "Sign-in, cloud sync, folder sync, and devices",
                itemCount = syncAndAccountItems().size
            ),
            rootCategory(
                destination = SharedSettingsDestination.EXTRA,
                title = "Extra",
                summary = "More-menu options, maintenance, diagnostics, and app info",
                itemCount = extraItems().size
            )
        ).filter { it.itemCount > 0 }
    }

    private fun rootCategory(
        destination: SharedSettingsDestination,
        title: String,
        summary: String,
        itemCount: Int
    ): SharedSettingsCategoryModel {
        return SharedSettingsCategoryModel(
            destination = destination,
            title = title,
            summary = summary,
            itemCount = itemCount
        )
    }

    private fun ttsAiTitle(): String {
        return if (hasAiSettingsItem()) "TTS & AI" else "TTS"
    }

    private fun ttsAiSummary(): String {
        return if (hasAiSettingsItem()) {
            "Global voice, speech replacements, keys, and reader AI"
        } else {
            "Global voice, speech behavior, and TTS replacements"
        }
    }

    private fun hasAiSettingsItem(): Boolean {
        return baseItem(SharedSettingsAction.AI_SETTINGS) != null
    }

    private fun categoryPage(
        destination: SharedSettingsDestination,
        title: String,
        summary: String,
        items: List<SharedSettingsItemModel>
    ): SharedSettingsPageModel {
        return SharedSettingsPageModel(
            destination = destination,
            title = title,
            summary = summary,
            kind = SharedSettingsPageKind.CATEGORY,
            parent = destination.parentDestination(),
            items = items
        )
    }

    private fun detailPage(
        destination: SharedSettingsDestination,
        title: String,
        summary: String,
        localOverrideNote: SharedSettingsItemModel?
    ): SharedSettingsPageModel {
        return SharedSettingsPageModel(
            destination = destination,
            title = title,
            summary = summary,
            kind = SharedSettingsPageKind.DETAIL,
            parent = destination.parentDestination(),
            localOverrideNote = localOverrideNote
        )
    }

    private fun epubAndTextItems(): List<SharedSettingsItemModel> {
        return buildList {
            baseItem(SharedSettingsAction.TEXT_READER_DEFAULTS)?.let { item ->
                add(
                    item.destinationRow(
                        destination = SharedSettingsDestination.EPUB_FORMAT,
                        title = "Format defaults",
                        summary = "Font, size, line spacing, margins, alignment, and reading mode"
                    )
                )
                add(
                    item.destinationRow(
                        destination = SharedSettingsDestination.EPUB_THEME_TEXTURE,
                        title = "Theme and texture",
                        summary = "Reading theme, texture, and page feel for new books"
                    )
                )
                add(
                    item.destinationRow(
                        destination = SharedSettingsDestination.EPUB_VISUAL_DEFAULTS,
                        title = "Visual defaults",
                        summary = "System UI, page info, images, and chapter-turn behavior"
                    )
                )
            }
            baseItem(SharedSettingsAction.READER_TOOLBAR)?.let { item ->
                add(item.destinationRow(SharedSettingsDestination.READER_TOOLBAR_DEFAULTS))
            }
        }
    }

    private fun pdfAndComicItems(): List<SharedSettingsItemModel> {
        return buildList {
            baseItem(SharedSettingsAction.PDF_READER_DEFAULTS)?.let { item ->
                add(
                    item.destinationRow(
                        destination = SharedSettingsDestination.PDF_APPEARANCE_DEFAULTS,
                        title = "PDF theme defaults",
                        summary = "PDF and comic theme defaults, separate from EPUB themes"
                    )
                )
                add(
                    item.destinationRow(
                        destination = SharedSettingsDestination.PDF_READER_TOOLS,
                        title = "PDF reader tools",
                        summary = "Auto-scroll, OCR, annotations, and PDF-only tools"
                    )
                )
            }
        }
    }

    private fun themeAndAppearanceItems(): List<SharedSettingsItemModel> {
        return itemsForActions(
            SharedSettingsAction.APP_THEME
        )
    }

    private fun ttsAndAiItems(): List<SharedSettingsItemModel> {
        return buildList {
            addAll(
                itemsForActions(
                    SharedSettingsAction.TTS_SETTINGS,
                    SharedSettingsAction.AI_SETTINGS
                )
            )
            baseItem(SharedSettingsAction.TTS_REPLACEMENTS)?.let { item ->
                add(item.destinationRow(SharedSettingsDestination.GLOBAL_TTS_REPLACEMENTS))
            }
        }
    }

    private fun libraryAndFileItems(): List<SharedSettingsItemModel> {
        return itemsForActions(
            SharedSettingsAction.RECENT_LIMIT,
            SharedSettingsAction.CUSTOM_FONTS
        )
    }

    private fun syncAndAccountItems(): List<SharedSettingsItemModel> {
        return itemsForActions(
            SharedSettingsAction.SIGN_IN,
            SharedSettingsAction.SIGN_OUT,
            SharedSettingsAction.CLOUD_SYNC,
            SharedSettingsAction.FOLDER_SYNC
        )
    }

    private fun extraItems(): List<SharedSettingsItemModel> {
        return itemsForActions(
            SharedSettingsAction.LANGUAGE,
            SharedSettingsAction.SCREEN_CAPTURE_PROTECTION,
            SharedSettingsAction.EXTERNAL_FILE_BEHAVIOR,
            SharedSettingsAction.STRICT_FILE_FILTER,
            SharedSettingsAction.TABS_TOGGLE,
            SharedSettingsAction.HIDE_READER_AI,
            SharedSettingsAction.CLEAR_BOOK_CACHE,
            SharedSettingsAction.CLEAR_REFLOW_CACHE,
            SharedSettingsAction.CLEAR_CLOUD_LOCAL_DATA,
            SharedSettingsAction.TEST_PANEL_DETECTION,
            SharedSettingsAction.TEST_SPEECH_BUBBLE_DETECTION,
            SharedSettingsAction.EXPORT_LOGS,
            SharedSettingsAction.DEVICE_MANAGEMENT,
            SharedSettingsAction.HELP_FEEDBACK,
            SharedSettingsAction.SUPPORT,
            SharedSettingsAction.ABOUT
        )
    }

    private fun helpAndAboutItems(): List<SharedSettingsItemModel> {
        return itemsForActions(
            SharedSettingsAction.HELP_FEEDBACK,
            SharedSettingsAction.SUPPORT,
            SharedSettingsAction.ABOUT
        )
    }

    private fun itemsForActions(vararg actions: SharedSettingsAction): List<SharedSettingsItemModel> {
        return actions.mapNotNull(::baseItem)
    }

    private fun localOverrideItem(): SharedSettingsItemModel? {
        return baseItem(SharedSettingsAction.LOCAL_OVERRIDE_NOTE)
    }

    private fun baseItem(action: SharedSettingsAction): SharedSettingsItemModel? {
        return sections.asSequence()
            .flatMap { it.items.asSequence() }
            .firstOrNull { it.action == action }
    }

    private fun SharedSettingsItemModel.destinationRow(
        destination: SharedSettingsDestination,
        title: String = this.title,
        summary: String = this.summary
    ): SharedSettingsItemModel {
        return copy(
            title = title,
            summary = summary,
            kind = SharedSettingsItemKind.NAVIGATION,
            destination = destination
        )
    }
}

private fun SharedSettingsSearchResult.searchIdentity(): String {
    return when (action) {
        SharedSettingsAction.TTS_REPLACEMENTS -> SharedSettingsAction.TTS_REPLACEMENTS.name
        else -> destination?.name ?: action?.name ?: title
    }
}

data class SharedSettingsHubInput(
    val platform: SharedSettingsPlatform,
    val featurePolicy: SharedFeaturePolicy = SharedFeaturePolicy.Standard,
    val isDebugBuild: Boolean = false,
    val isSignedIn: Boolean = false,
    val isProUser: Boolean = false,
    val syncAvailable: Boolean = true,
    val folderSyncAvailable: Boolean = true,
    val aiSettingsAvailable: Boolean = true,
    val ttsSettingsAvailable: Boolean = true,
    val includePdfReaderDefaults: Boolean = true,
    val includeReaderToolbar: Boolean = true,
    val includeLanguage: Boolean = true,
    val includeScreenCaptureProtection: Boolean = false,
    val includeExternalFileBehavior: Boolean = true,
    val includeRecentLimit: Boolean = true,
    val includeCustomFonts: Boolean = true,
    val includeStrictFileFilter: Boolean = true,
    val includeReaderTabs: Boolean = true,
    val includeHideReaderAi: Boolean = true,
    val includeCloudLocalDataClear: Boolean = false,
    val supportProjectAvailable: Boolean = true,
    val isTabsEnabled: Boolean = true,
    val isSyncEnabled: Boolean = false,
    val isFolderSyncEnabled: Boolean = false,
    val useStrictFileFilter: Boolean = false,
    val isScreenCaptureProtectionEnabled: Boolean = false,
    val hideReaderAi: Boolean = false
)

fun sharedSettingsHubModel(input: SharedSettingsHubInput): SharedSettingsHubModel {
    val sections = listOf(
        SharedSettingsSectionModel(
            section = SharedSettingsSection.READER,
            items = buildList {
                add(
                    SharedSettingsItemModel(
                        action = SharedSettingsAction.TEXT_READER_DEFAULTS,
                        title = "Text and EPUB defaults",
                        summary = "Format, EPUB theme, texture, visual behavior, and text layout",
                        kind = SharedSettingsItemKind.CONTROL
                    )
                )
                if (input.includePdfReaderDefaults) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.PDF_READER_DEFAULTS,
                            title = "PDF and comic defaults",
                            summary = "PDF theme, visual defaults, tools, auto-scroll, OCR, and annotation behavior where available",
                            kind = SharedSettingsItemKind.NAVIGATION
                        )
                    )
                }
                if (input.includeReaderToolbar) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.READER_TOOLBAR,
                            title = "Reader toolbar and tools",
                            summary = "Choose visible tools, bottom-bar tools, and reader overflow tools",
                            kind = SharedSettingsItemKind.CONTROL
                        )
                    )
                }
                add(
                    SharedSettingsItemModel(
                        action = SharedSettingsAction.LOCAL_OVERRIDE_NOTE,
                        title = "Per-book overrides",
                        summary = "Local overrides are available from the active reader screen and still win for that book.",
                        kind = SharedSettingsItemKind.INFO
                    )
                )
            }
        ),
        SharedSettingsSectionModel(
            section = SharedSettingsSection.APP_LIBRARY,
            items = buildList {
                add(
                    SharedSettingsItemModel(
                        action = SharedSettingsAction.APP_THEME,
                        title = "App theme",
                        summary = "Theme mode, contrast, reading text dimming, and custom app colors"
                    )
                )
                if (input.includeCustomFonts) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.CUSTOM_FONTS,
                            title = "Custom fonts",
                            summary = "Import, manage, and reuse local reading fonts"
                        )
                    )
                }
                if (input.includeRecentLimit) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.RECENT_LIMIT,
                            title = "Recent files limit",
                            summary = "Control how many recent books appear on Home"
                        )
                    )
                }
            }
        ),
        SharedSettingsSectionModel(
            section = SharedSettingsSection.SYNC_ACCOUNTS,
            items = buildList {
                if (input.syncAvailable && input.featurePolicy.aiAndCloud) {
                    if (input.isSignedIn) {
                        add(
                            SharedSettingsItemModel(
                                action = SharedSettingsAction.SIGN_OUT,
                                title = "Sign out",
                                summary = "Disconnect this device from your account",
                                kind = SharedSettingsItemKind.DESTRUCTIVE
                            )
                        )
                    } else {
                        add(
                            SharedSettingsItemModel(
                                action = SharedSettingsAction.SIGN_IN,
                                title = "Sign in",
                                summary = "Connect sync and account features"
                            )
                        )
                    }
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.CLOUD_SYNC,
                            title = "Cloud library sync",
                            summary = if (input.isProUser) "Sync library metadata across signed-in devices." else "A Pro account is required for cloud sync.",
                            kind = SharedSettingsItemKind.TOGGLE,
                            enabled = input.isProUser,
                            checked = input.isSyncEnabled
                        )
                    )
                }
                if (input.folderSyncAvailable) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.FOLDER_SYNC,
                            title = "Folder backup and sync",
                            summary = "Keep selected local folders represented in the library",
                            kind = SharedSettingsItemKind.TOGGLE,
                            checked = input.isFolderSyncEnabled
                        )
                    )
                }
                if (input.isDebugBuild && input.featurePolicy.aiAndCloud && input.syncAvailable) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.DEVICE_MANAGEMENT,
                            title = "Device management",
                            summary = "Inspect registered devices for this account"
                        )
                    )
                }
            }
        ),
        SharedSettingsSectionModel(
            section = SharedSettingsSection.AI_TTS,
            items = buildList {
                if (input.aiSettingsAvailable && input.featurePolicy.aiAndCloud) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.AI_SETTINGS,
                            title = "AI keys and models",
                            summary = "Configure reader AI and cloud TTS model access"
                        )
                    )
                }
                if (input.ttsSettingsAvailable) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.TTS_SETTINGS,
                            title = "TTS voice settings",
                            summary = "Choose cloud or device voices and speech behavior"
                        )
                    )
                }
                add(
                    SharedSettingsItemModel(
                        action = SharedSettingsAction.TTS_REPLACEMENTS,
                        title = "Global TTS replacements",
                        summary = "Words and phrases replaced only during speech playback",
                        kind = SharedSettingsItemKind.CONTROL
                    )
                )
            }
        ),
        SharedSettingsSectionModel(
            section = SharedSettingsSection.STORAGE_ADVANCED,
            items = buildList {
                add(
                    SharedSettingsItemModel(
                        action = SharedSettingsAction.CLEAR_BOOK_CACHE,
                        title = "Clear book cache",
                        summary = "Remove generated book cache files and recreate them on demand",
                        kind = SharedSettingsItemKind.DESTRUCTIVE
                    )
                )
                add(
                    SharedSettingsItemModel(
                        action = SharedSettingsAction.CLEAR_REFLOW_CACHE,
                        title = "Clear reflow cache",
                        summary = "Remove generated PDF text-view files",
                        kind = SharedSettingsItemKind.DESTRUCTIVE
                    )
                )
                if (input.isDebugBuild) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.TEST_PANEL_DETECTION,
                            title = "Test panel detection",
                            summary = "Run the local panel-detection diagnostic"
                        )
                    )
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.TEST_SPEECH_BUBBLE_DETECTION,
                            title = "Test speech-bubble detection",
                            summary = "Run the local speech-bubble detection diagnostic"
                        )
                    )
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.EXPORT_LOGS,
                            title = "Export logs",
                            summary = "Export recent diagnostic logs",
                            kind = SharedSettingsItemKind.NAVIGATION
                        )
                    )
                    if (input.includeCloudLocalDataClear && input.featurePolicy.aiAndCloud) {
                        add(
                            SharedSettingsItemModel(
                                action = SharedSettingsAction.CLEAR_CLOUD_LOCAL_DATA,
                                title = "Clear cloud and local data",
                                summary = "Delete cloud records and matching local library data",
                                kind = SharedSettingsItemKind.DESTRUCTIVE
                            )
                        )
                    }
                }
            }
        ),
        SharedSettingsSectionModel(
            section = SharedSettingsSection.EXTRA,
            items = buildList {
                if (input.includeLanguage) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.LANGUAGE,
                            title = "Language",
                            summary = "Choose the app language"
                        )
                    )
                }
                if (input.includeScreenCaptureProtection) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.SCREEN_CAPTURE_PROTECTION,
                            title = "Screen capture protection",
                            summary = "Block screenshots and screen recording on sensitive reader screens",
                            kind = SharedSettingsItemKind.TOGGLE,
                            checked = input.isScreenCaptureProtectionEnabled
                        )
                    )
                }
                if (input.includeExternalFileBehavior) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.EXTERNAL_FILE_BEHAVIOR,
                            title = "External file behavior",
                            summary = "Choose whether external opens are copied into the app library"
                        )
                    )
                }
                if (input.includeStrictFileFilter) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.STRICT_FILE_FILTER,
                            title = "Strict file filter",
                            summary = "Use only known reader file types in import pickers",
                            kind = SharedSettingsItemKind.TOGGLE,
                            checked = input.useStrictFileFilter
                        )
                    )
                }
                if (input.includeReaderTabs) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.TABS_TOGGLE,
                            title = "Reader tabs",
                            summary = if (input.isTabsEnabled) "Opening PDFs keeps active tabs." else "PDFs replace the active reader session.",
                            kind = SharedSettingsItemKind.TOGGLE,
                            checked = input.isTabsEnabled
                        )
                    )
                }
                if (input.includeHideReaderAi && input.featurePolicy.aiAndCloud) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.HIDE_READER_AI,
                            title = "Reader AI visibility",
                            summary = if (input.hideReaderAi) "Reader AI tools are hidden." else "Reader AI tools are shown where available.",
                            kind = SharedSettingsItemKind.TOGGLE,
                            checked = !input.hideReaderAi
                        )
                    )
                }
            }
        ),
        SharedSettingsSectionModel(
            section = SharedSettingsSection.HELP,
            items = buildList {
                if (input.featurePolicy.projectLinks) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.HELP_FEEDBACK,
                            title = "Help and feedback",
                            summary = "Send feedback or report an issue"
                        )
                    )
                    if (input.supportProjectAvailable) {
                        add(
                            SharedSettingsItemModel(
                                action = SharedSettingsAction.SUPPORT,
                                title = "Support project",
                                summary = "Open support options for the project"
                            )
                        )
                    }
                }
                add(
                    SharedSettingsItemModel(
                        action = SharedSettingsAction.ABOUT,
                        title = "About",
                        summary = "Version, source, licenses, and project information"
                    )
                )
            }
        )
    ).filter { it.items.isNotEmpty() }

    return SharedSettingsHubModel(
        platform = input.platform,
        sections = sections
    )
}
