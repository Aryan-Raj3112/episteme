package com.aryan.reader.shared.epub

/**
 * The ordered sections exposed by the EPUB (text reader) navigation drawer.
 *
 * Mirrors [com.aryan.reader.shared.pdf.PdfDrawerSection]: when open text tabs are available they
 * appear first, followed by chapters, bookmarks, annotations, and images. Text tabs use their own
 * section list rather than the PDF one because the two readers expose genuinely different sections
 * past the tabs — a PDF has pages and highlights, a reflowed book has chapters and images.
 */
enum class EpubDrawerSection(
    val stringKey: String,
    val fallbackLabel: String,
) {
    TABS("tab_tabs", "Tabs"),
    CHAPTERS("tab_chapters", "Chapters"),
    BOOKMARKS("tab_bookmarks", "Bookmarks"),
    ANNOTATIONS("tab_annotations", "Annotations"),
    IMAGES("tab_images", "Images"),
}

data class EpubDrawerCapabilities(
    /** Whether the reader's tab feature is enabled for this document/session. */
    val tabsEnabled: Boolean = false,
    /** Whether there is at least one open text tab to show in the drawer. */
    val hasOpenTabs: Boolean = false,
)

/**
 * Returns the drawer sections for the supplied capabilities in Android benchmark order. Only the
 * tabs section is optional; empty chapters/bookmarks/annotations/images keep their sections so the
 * drawer does not reorder itself as a book is annotated.
 */
fun epubDrawerSections(
    capabilities: EpubDrawerCapabilities = EpubDrawerCapabilities(),
): List<EpubDrawerSection> = buildList {
    if (capabilities.tabsEnabled && capabilities.hasOpenTabs) {
        add(EpubDrawerSection.TABS)
    }
    add(EpubDrawerSection.CHAPTERS)
    add(EpubDrawerSection.BOOKMARKS)
    add(EpubDrawerSection.ANNOTATIONS)
    add(EpubDrawerSection.IMAGES)
}
