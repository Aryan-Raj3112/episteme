package com.aryan.reader.paginatedreader

/**
 * Canonical page-visibility rules for highlights in paginated rendering.
 *
 * Pagination splits a chapter into pages while highlights are anchored chapter-wide
 * (absolute [startOffset, endOffset), plus structural [blockIndex]/CFI scope). A highlight
 * must only be evaluated against pages its anchor can touch; otherwise repeated sentences
 * paint on every page of the chapter (ghosts/duplicates) via text-quote fallbacks.
 *
 * Android (`app/.../paginatedreader`) is the benchmark consumer; iOS follows the same
 * rules through these predicates so both platforms agree.
 */

/**
 * Whether a highlight's absolute text range can touch a page's absolute char range.
 *
 * A missing range (legacy highlights without offsets) cannot be page-scoped, so it
 * conservatively returns true and leaves the decision to per-block CFI/quote mapping.
 * Collapsed ranges (insertion points) use closed-open containment.
 */
fun highlightTextRangeOverlapsPage(
    highlightStartOffset: Int?,
    highlightEndOffset: Int?,
    pageStartOffset: Int,
    pageEndOffset: Int
): Boolean {
    if (highlightStartOffset == null || highlightEndOffset == null) return true
    if (pageStartOffset >= pageEndOffset) return true
    return if (highlightStartOffset == highlightEndOffset) {
        highlightStartOffset >= pageStartOffset && highlightStartOffset < pageEndOffset
    } else {
        highlightStartOffset < pageEndOffset && highlightEndOffset > pageStartOffset
    }
}

/**
 * Whether a highlight carries structural scope (a semantic block index or a source CFI).
 *
 * Mirrors the shared reader's structural-scope checks (`blockIndex != null` or a `/`-rooted
 * source CFI): text-quote fallbacks must not relocate structurally-scoped highlights to
 * unrelated blocks/pages.
 */
fun highlightHasStructuralScope(blockIndex: Int?, sourceCfi: String?): Boolean {
    return blockIndex != null || (sourceCfi?.startsWith("/") == true)
}
