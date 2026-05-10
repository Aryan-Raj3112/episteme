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
        summary = "Appearance, language, imports, tabs, and local library behavior"
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
    )
}

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
    val checked: Boolean? = null
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
    val includeHideReaderAi: Boolean = true,
    val isTabsEnabled: Boolean = false,
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
                        summary = "Format, theme, texture, visual behavior, and text layout",
                        kind = SharedSettingsItemKind.CONTROL
                    )
                )
                if (input.includePdfReaderDefaults) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.PDF_READER_DEFAULTS,
                            title = "PDF and comic defaults",
                            summary = "Theme, visual defaults, tools, auto-scroll, OCR, and annotation behavior where available",
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
                        action = SharedSettingsAction.TTS_REPLACEMENTS,
                        title = "Global TTS replacements",
                        summary = "Words and phrases replaced only during speech playback",
                        kind = SharedSettingsItemKind.CONTROL
                    )
                )
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
                if (input.includeLanguage) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.LANGUAGE,
                            title = "Language",
                            summary = "Choose the app language"
                        )
                    )
                }
                if (input.includeCustomFonts) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.CUSTOM_FONTS,
                            title = "Custom fonts",
                            summary = "Import, manage, and reuse local reading fonts"
                        )
                    )
                }
                add(
                    SharedSettingsItemModel(
                        action = SharedSettingsAction.TABS_TOGGLE,
                        title = "Reader tabs",
                        summary = if (input.isTabsEnabled) "Opening books keeps active tabs." else "Books replace the active reader session.",
                        kind = SharedSettingsItemKind.TOGGLE,
                        checked = input.isTabsEnabled
                    )
                )
                if (input.includeRecentLimit) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.RECENT_LIMIT,
                            title = "Recent files limit",
                            summary = "Control how many recent books appear on Home"
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
                if (input.includeExternalFileBehavior) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.EXTERNAL_FILE_BEHAVIOR,
                            title = "External file behavior",
                            summary = "Choose whether external opens are copied into the app library"
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
            }
        ),
        SharedSettingsSectionModel(
            section = SharedSettingsSection.SYNC_ACCOUNTS,
            items = buildList {
                if (input.isSignedIn) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.SIGN_OUT,
                            title = "Sign out",
                            summary = "Disconnect this device from your account",
                            kind = SharedSettingsItemKind.DESTRUCTIVE
                        )
                    )
                } else if (input.featurePolicy.aiAndCloud) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.SIGN_IN,
                            title = "Sign in",
                            summary = "Connect sync and account features"
                        )
                    )
                }
                if (input.syncAvailable && input.featurePolicy.aiAndCloud) {
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
                if (input.isDebugBuild && input.featurePolicy.aiAndCloud) {
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
                if (input.ttsSettingsAvailable) {
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.TTS_SETTINGS,
                            title = "TTS voice settings",
                            summary = "Choose cloud or device voices and speech behavior"
                        )
                    )
                }
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
                            action = SharedSettingsAction.EXPORT_LOGS,
                            title = "Export logs",
                            summary = "Export recent diagnostic logs",
                            kind = SharedSettingsItemKind.NAVIGATION
                        )
                    )
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.DEBUG_ACTIONS,
                            title = "Debug actions",
                            summary = "Platform-specific debug tools and experiments"
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
                    add(
                        SharedSettingsItemModel(
                            action = SharedSettingsAction.SUPPORT,
                            title = "Support project",
                            summary = "Open support options for the project"
                        )
                    )
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
