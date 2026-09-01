package com.aryan.reader.shared.ui

/**
 * Stable semantic labels used by the mobile Library Beta drawer.
 *
 * Platforms resolve these labels through their own localization resources. Keeping the
 * semantic value here avoids letting a platform-specific string key define drawer order.
 */
enum class MobileUnifiedLibraryDrawerLabel {
    HOME,
    AUDIOBOOKS,
    SHELVES,
    FOLDERS,
    CATALOGS,
    THEME,
    SETTINGS,
    FONTS,
    AI,
}

enum class MobileUnifiedLibraryDrawerDestination(
    val section: MobileUnifiedLibrarySection,
    val label: MobileUnifiedLibraryDrawerLabel,
) {
    HOME(MobileUnifiedLibrarySection.HOME, MobileUnifiedLibraryDrawerLabel.HOME),
    AUDIOBOOKS(MobileUnifiedLibrarySection.AUDIOBOOKS, MobileUnifiedLibraryDrawerLabel.AUDIOBOOKS),
    SHELVES(MobileUnifiedLibrarySection.SHELVES, MobileUnifiedLibraryDrawerLabel.SHELVES),
    FOLDERS(MobileUnifiedLibrarySection.FOLDERS, MobileUnifiedLibraryDrawerLabel.FOLDERS),
    CATALOGS(MobileUnifiedLibrarySection.CATALOGS, MobileUnifiedLibraryDrawerLabel.CATALOGS),
}

enum class MobileUnifiedLibraryDrawerAppearance(
    val label: MobileUnifiedLibraryDrawerLabel,
) {
    THEME(MobileUnifiedLibraryDrawerLabel.THEME),
    SETTINGS(MobileUnifiedLibraryDrawerLabel.SETTINGS),
    FONTS(MobileUnifiedLibraryDrawerLabel.FONTS),
    AI(MobileUnifiedLibraryDrawerLabel.AI),
}

data class MobileUnifiedLibraryDrawerCapabilities(
    val catalogsAvailable: Boolean = true,
    val aiSettingsAvailable: Boolean = false,
)

data class MobileUnifiedLibraryDrawerModel(
    val destinations: List<MobileUnifiedLibraryDrawerDestination>,
    val appearance: List<MobileUnifiedLibraryDrawerAppearance>,
)

/**
 * Android's Library Beta drawer order is the mobile benchmark. Optional destinations are
 * removed here so every platform renders the same ordered sections for its capabilities.
 */
fun mobileUnifiedLibraryDrawerModel(
    capabilities: MobileUnifiedLibraryDrawerCapabilities = MobileUnifiedLibraryDrawerCapabilities(),
): MobileUnifiedLibraryDrawerModel = MobileUnifiedLibraryDrawerModel(
    destinations = buildList {
        add(MobileUnifiedLibraryDrawerDestination.HOME)
        add(MobileUnifiedLibraryDrawerDestination.AUDIOBOOKS)
        add(MobileUnifiedLibraryDrawerDestination.SHELVES)
        add(MobileUnifiedLibraryDrawerDestination.FOLDERS)
        if (capabilities.catalogsAvailable) add(MobileUnifiedLibraryDrawerDestination.CATALOGS)
    },
    appearance = buildList {
        add(MobileUnifiedLibraryDrawerAppearance.THEME)
        add(MobileUnifiedLibraryDrawerAppearance.SETTINGS)
        add(MobileUnifiedLibraryDrawerAppearance.FONTS)
        if (capabilities.aiSettingsAvailable) add(MobileUnifiedLibraryDrawerAppearance.AI)
    },
)
