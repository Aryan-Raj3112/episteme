package com.aryan.reader.shared.pdf

/**
 * The ordered sections exposed by the PDF navigation drawer.
 *
 * Android is the mobile benchmark for this chrome: when open PDF tabs are available,
 * they appear first, followed by chapters, bookmarks, highlights, and pages. The
 * section model lives in common code so platform readers cannot silently drift in order.
 */
enum class PdfDrawerSection(
    val stringKey: String,
    val fallbackLabel: String,
) {
    TABS("tab_tabs", "Tabs"),
    CHAPTERS("tab_chapters", "Chapters"),
    BOOKMARKS("tab_bookmarks", "Bookmarks"),
    HIGHLIGHTS("tab_highlights", "Highlights"),
    PAGES("tab_pages", "Pages"),
}

data class PdfDrawerCapabilities(
    /** Whether the reader's tab feature is enabled for this document/session. */
    val tabsEnabled: Boolean = false,
    /** Whether there is at least one open tab to show in the drawer. */
    val hasOpenTabs: Boolean = false,
)

/**
 * Returns the drawer sections for the supplied capabilities in Android benchmark order.
 * Empty chapters/bookmarks/highlights collections still keep their sections visible; the
 * tabs section is the only optional section and requires both capability flags.
 */
fun pdfDrawerSections(
    capabilities: PdfDrawerCapabilities = PdfDrawerCapabilities(),
): List<PdfDrawerSection> = buildList {
    if (capabilities.tabsEnabled && capabilities.hasOpenTabs) {
        add(PdfDrawerSection.TABS)
    }
    add(PdfDrawerSection.CHAPTERS)
    add(PdfDrawerSection.BOOKMARKS)
    add(PdfDrawerSection.HIGHLIGHTS)
    add(PdfDrawerSection.PAGES)
}
