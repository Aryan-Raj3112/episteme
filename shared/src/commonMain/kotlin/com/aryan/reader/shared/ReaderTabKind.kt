package com.aryan.reader.shared

/**
 * Which reader engine owns a tab.
 *
 * Open tabs live in two independent sets because each set belongs to one engine. Switching a tab
 * therefore never swaps the renderer, the chrome, or the pagination model underneath the reader:
 * a PDF tab tap stays inside the page renderer and a text tab tap stays inside the reflow renderer,
 * exactly as reopening that kind of book would. Both sets share one on/off setting.
 */
enum class ReaderTabKind(val persistedValue: String) {
    /** Page renderer: PDF plus the comic and paged-office containers it opens. */
    FIXED_LAYOUT("fixed"),

    /** Reflow renderer: EPUB, TXT, Markdown, HTML, MOBI, FB2 and the office text containers. */
    TEXT("text"),
    ;

    companion object {
        val DEFAULT: ReaderTabKind = FIXED_LAYOUT

        fun fromPersisted(value: String?): ReaderTabKind? =
            entries.firstOrNull { it.persistedValue == value }
    }
}

/**
 * The tab set [this] file type belongs to, or null when the type has no mobile reader.
 *
 * Derived from the file-capability table rather than a hand-written type list, so a format that
 * gains or loses a reader surface moves tab eligibility with it. iOS shares the Android surfaces
 * (see [FileTypeCapability.iosSurface]), so this answers for both mobile platforms.
 */
fun FileType.readerTabKind(): ReaderTabKind? =
    when (SharedFileCapabilities.surfaceFor(this, ReaderPlatform.ANDROID)) {
        ReaderFeatureSurface.PDF_VIEWER -> ReaderTabKind.FIXED_LAYOUT
        ReaderFeatureSurface.EPUB_READER,
        ReaderFeatureSurface.TEXT_READER -> ReaderTabKind.TEXT
        null -> null
    }

fun BookItem.readerTabKind(): ReaderTabKind? = type.readerTabKind()

/**
 * Reflow views are derived from another book (`<bookId>_reflow`) and are reopened from that book,
 * so they never own a tab: a "Text view" of a PDF is a reading mode, not a second document.
 */
fun String.isDerivedReflowBookId(): Boolean = endsWith("_reflow")

/** A book can join a tab set unless it already has a tab or the set still has room. */
internal fun tabSetAccepts(openBookIds: List<String>, bookId: String, maxOpenTabs: Int): Boolean =
    bookId in openBookIds || openBookIds.size < maxOpenTabs
