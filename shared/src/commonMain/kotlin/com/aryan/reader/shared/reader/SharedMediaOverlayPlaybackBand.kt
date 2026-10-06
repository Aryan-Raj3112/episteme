package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.playbackBandHighlight

/**
 * Painting a media overlay's current position, without touching a single paint path.
 *
 * This is the decision that keeps the feature cheap. There are six places in the app that paint a
 * playback position — the two shared native readers, the shared WebView, and three Android reader
 * files that are collectively several thousand lines — and they all already draw read-aloud. So
 * rather than add a seventh parameter to each, a media overlay produces a **transient band**: a
 * [UserHighlight] marked as a band, which the existing paint paths draw and the existing hit-testing
 * already refuses to select.
 *
 * That reuses the read-aloud path wholesale, which is the point. Two engines painting through the
 * same code is the only way they cannot drift in appearance, and it means the feature costs one
 * projection instead of six paint sites.
 *
 * The one thing this must not do is *repair* the range. `UserHighlight` resolves through
 * `EpubChapterTextIndex` first, which is offset-and-cfi based and exact; only a surface with no
 * index falls through to quote matching. An overlay anchor is derived from the same parse that
 * produced the blocks, so the quote we carry is already exact and that fallback never fires.
 */

/**
 * The band for one narration position.
 *
 * @param highlight paint-only; see [UserHighlight.isTransientPlaybackBand].
 * @param locator where the reader should go if the fragment is followed.
 */
data class SharedMediaOverlayPlaybackBand(
    val highlight: UserHighlight,
    val locator: ReaderLocator
)

/**
 * Builds the paintable band for a narration position.
 *
 * @param projection the resolved position. A null projection or a projection with no fragment
 *   yields null: nothing is narrated, or nothing is anchored, and in both cases the reader should
 *   see no band rather than a stale one.
 * @param pageIndex the page the fragment is on, when the caller knows it. Null is fine — the band
 *   resolves by chapter and cfi, and page index is only used to prefer an already-visible page.
 * @param textQuote the fragment's own text. Optional; see the file note. Supplied because it makes
 *   the band resolvable on surfaces with no chapter index, which is the one place a quote fallback
 *   exists.
 * @param sessionId distinguishes one playback session's bands from another's, so a band from a
 *   finished session cannot be mistaken for the current one.
 */
fun sharedMediaOverlayPlaybackBand(
    projection: SharedMediaOverlayProjection?,
    pageIndex: Int? = null,
    textQuote: String? = null,
    sessionId: Long
): SharedMediaOverlayPlaybackBand? {
    val fragment = projection?.fragment?.takeIf { !it.isEmpty } ?: return null
    val chapterIndex = projection.chapterIndex ?: return null
    val highlight = playbackBandHighlight(
        sessionId = sessionId,
        bandIndex = projection.clipIndex,
        chapterIndex = chapterIndex,
        cfi = fragment.blockCfi,
        text = textQuote,
        startOffset = fragment.startAbs,
        endOffset = fragment.endAbs
    )
    return SharedMediaOverlayPlaybackBand(
        highlight = highlight,
        locator = highlight.locator.copy(pageIndex = pageIndex)
    )
}

/**
 * The band as a highlight, for the paint paths that take a plain list.
 *
 * The convenience most callers want. [SharedMediaOverlayPlaybackBand] exists for the ones that also
 * need the locator, which is the follow path and nothing else.
 */
fun sharedMediaOverlayBandHighlight(
    projection: SharedMediaOverlayProjection?,
    pageIndex: Int? = null,
    textQuote: String? = null,
    sessionId: Long
): UserHighlight? = sharedMediaOverlayPlaybackBand(
    projection = projection,
    pageIndex = pageIndex,
    textQuote = textQuote,
    sessionId = sessionId
)?.highlight

/**
 * The colour a media overlay band is drawn in.
 *
 * `RS §9` lets a publisher name an `active-class` for the spoken fragment, and a well-produced book
 * ships a CSS rule for it. We do not run publisher CSS against our own renderer — the class is
 * applied in the WebView, where the document *is* styled by the book's stylesheet, and matching it
 * here would mean parsing arbitrary CSS to find one colour. The native readers use the reader's own
 * read-aloud colour instead, so the band looks like the read-aloud band it sits alongside.
 *
 * @param activeClass the publisher's declared class, kept for diagnostics and for the WebView path.
 * @param readAloudColor the reader's existing read-aloud band colour.
 */
fun sharedMediaOverlayBandColor(
    activeClass: String?,
    readAloudColor: androidx.compose.ui.graphics.Color
): androidx.compose.ui.graphics.Color = readAloudColor

/**
 * Whether a media overlay should be offered at all for a book.
 *
 * One gate, and deliberately at the cheapest point available. The index is built for every book from
 * the OPF alone, so "does this book narrate itself" is answerable without parsing a single SMIL body.
 *
 * @param index the book's package-level overlay facts.
 * @param playButtonVisible whether the reader currently offers a play control at all. A book with no
 *   overlay never needs the button, and adding it would be a visible change to every book in the
 *   library for the sake of the few that narrate.
 */
fun sharedMediaOverlayIsOffered(
    index: SharedMediaOverlayIndex?,
    playButtonVisible: Boolean
): Boolean = playButtonVisible && index?.hasOverlays == true

/**
 * The cover "narrated by" line, for a library card.
 *
 * Null whenever the fact is absent, so a caller renders nothing rather than an empty label. Narrator
 * and duration are both optional in `RS §9`, and a book with only one of them still reads correctly.
 */
fun sharedMediaOverlayNarrationLabel(index: SharedMediaOverlayIndex?): String? {
    val narrator = index?.narrator?.takeIf(String::isNotBlank) ?: return null
    return "Narrated by $narrator"
}

/**
 * The quote for a clip's band, read from the block the fragment lands in.
 *
 * Only ever used as a fallback for surfaces with no chapter text index, since the band otherwise
 * resolves by offset and cfi. Reading it from the block rather than from [SharedEpubChapter.plainText]
 * is deliberate: `plainText` has book replacements applied to it, so a reader who has replaced a
 * word would get a quote that no longer matches the rendered text, and the one path that compares
 * quotes would then fail on a band that is actually correct.
 *
 * Returns null when the block holding the fragment cannot be found or does not cover the whole
 * range, which is the same thing [findElementTextRange] would have refused to paint.
 */
fun sharedMediaOverlayTextQuote(
    chapters: List<SharedEpubChapter>,
    chapterIndex: Int?,
    fragment: SharedPlaybackFragment?
): String? {
    val index = chapterIndex ?: return null
    val target = fragment ?: return null
    val chapter = chapters.getOrNull(index) ?: return null
    val block = chapter.semanticBlocks.firstOrNull { it.cfi != null && it.cfi == target.blockCfi }
        ?: return null
    val text = (block as? SemanticTextBlock)?.text ?: return null
    val localStart = target.startAbs - block.startCharOffsetInSource
    val localEnd = target.endAbs - block.startCharOffsetInSource
    // Both ends must be inside the block. Clamping instead would hand back a *truncated* quote, and
    // the one path that matches quotes could then find that fragment somewhere else entirely —
    // painting the band on text the narrator was not saying. No band is the safer answer, and the
    // offset-and-cfi path still paints correctly without it.
    if (localStart < 0 || localEnd > text.length) return null
    if (localEnd <= localStart) return null
    return text.substring(localStart, localEnd)
}