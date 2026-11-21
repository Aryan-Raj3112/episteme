package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.aryan.reader.shared.BuiltInPdfReaderThemes
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.SharedSettingsAction
import com.aryan.reader.shared.SharedSettingsDestination
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.readerThemeById

/**
 * Current-value previews for settings rows whose value only the host knows.
 *
 * Shared so every host renders the same preview text for the same state: Android supplies Android
 * defaults and iOS supplies the persisted iOS defaults, but "Sepia", "12 of 24 tools", and the
 * version string must not be formatted differently on each platform.
 *
 * Labels are resolved by the caller because string resolution is a platform catalog. [sharedSettingsItemValues]
 * stays pure so it can be unit tested without a composition.
 */
internal fun sharedSettingsItemValues(
    epubDefaults: ReaderSettings,
    pdfDefaults: ReaderSettings,
    toolbarPreferences: ReaderToolbarPreferences,
    customFontCount: Int,
    fontSizeLabel: String,
    toolCountLabel: String,
    customFontCountLabel: String,
    versionLabel: String?,
): Map<String, String> = buildMap {
    put(
        SharedSettingsDestination.EPUB_FORMAT.name,
        listOfNotNull(
            epubDefaults.fontFamily.takeIf { it.isNotBlank() },
            fontSizeLabel.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    )
    readerThemeById(epubDefaults.themeId)?.name?.let { themeName ->
        put(SharedSettingsDestination.EPUB_THEME_TEXTURE.name, themeName)
    }
    BuiltInPdfReaderThemes.firstOrNull { it.id == pdfDefaults.themeId }?.name?.let { themeName ->
        put(SharedSettingsDestination.PDF_APPEARANCE_DEFAULTS.name, themeName)
    }
    put(SharedSettingsDestination.READER_TOOLBAR_DEFAULTS.name, toolCountLabel)
    if (customFontCount > 0) {
        put(SharedSettingsAction.CUSTOM_FONTS.name, customFontCountLabel)
    }
    versionLabel?.takeIf { it.isNotBlank() }?.let { label ->
        put(SharedSettingsAction.ABOUT.name, label)
    }
}

/**
 * Resolves the localized labels for [sharedSettingsItemValues] from the shared resolver and
 * remembers the resulting map, so a host can hand it straight to `SharedSettingsHubInput.itemValues`.
 */
@Composable
fun rememberSharedSettingsItemValues(
    epubDefaults: ReaderSettings,
    pdfDefaults: ReaderSettings,
    toolbarPreferences: ReaderToolbarPreferences,
    customFontCount: Int,
    appVersion: String?,
): Map<String, String> {
    val totalTools = ReaderTool.entries.size
    val visibleTools = (totalTools - toolbarPreferences.hiddenToolIds.size).coerceIn(0, totalTools)
    val fontSizeLabel = readerString(
        "settings_hub_value_font_size",
        "%1\$dsp",
        epubDefaults.fontSize
    )
    val toolCountLabel = readerString(
        "settings_hub_value_tools",
        "%1\$d of %2\$d tools",
        visibleTools,
        totalTools
    )
    val customFontCountLabel = readerString(
        "settings_hub_value_fonts",
        "%1\$d fonts",
        customFontCount
    )
    val versionLabel = appVersion
        ?.takeIf { it.isNotBlank() }
        ?.let { version -> readerString("about_version_name", "Version %1\$s", version) }

    return remember(
        epubDefaults,
        pdfDefaults,
        customFontCount,
        fontSizeLabel,
        toolCountLabel,
        customFontCountLabel,
        versionLabel,
    ) {
        sharedSettingsItemValues(
            epubDefaults = epubDefaults,
            pdfDefaults = pdfDefaults,
            toolbarPreferences = toolbarPreferences,
            customFontCount = customFontCount,
            fontSizeLabel = fontSizeLabel,
            toolCountLabel = toolCountLabel,
            customFontCountLabel = customFontCountLabel,
            versionLabel = versionLabel,
        )
    }
}
