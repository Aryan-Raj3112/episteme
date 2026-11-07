package com.aryan.reader.shared.search

/**
 * Decides how much of a book's content matches a single library row shows.
 *
 * A book's hits are chunk-based, so a chapter that repeats a word produces
 * several near-identical chunks that would each render the same sentence.
 * Collapsing to the best hit per location is what keeps the list readable, and
 * the same location-ranked list backs the expanded view so growing a row never
 * reorders what was already visible.
 */
object SharedContentSearchHitVisibility {

    /** Hits shown until the row is expanded. */
    const val COLLAPSED_HITS = 1

    /** Ceiling per row; the rest stays behind the "more" affordance. */
    const val EXPANDED_HITS = 8

    /** Hits to render, best-first, one per chapter/page location. */
    fun hitsFor(
        result: SharedGlobalSearchBookResult,
        expanded: Boolean,
    ): List<SharedGlobalSearchHit> {
        val limit = if (expanded) EXPANDED_HITS else COLLAPSED_HITS
        return result.hits
            .distinctByLocation()
            .take(limit)
    }

    /**
     * Matches still reachable from the current view. Collapsed: how many more
     * locations expanding would reveal. Expanded: how many matches exist past
     * the visibility ceiling, which is 0 unless the book has more than
     * [EXPANDED_HITS] distinct locations.
     */
    fun hiddenMatchCount(
        result: SharedGlobalSearchBookResult,
        expanded: Boolean,
    ): Int {
        val distinct = result.hits.distinctByLocation().size
        return if (expanded) {
            // Locations past the ceiling can never be opened.
            (distinct - EXPANDED_HITS).coerceAtLeast(0)
        } else {
            (minOf(distinct, EXPANDED_HITS) - COLLAPSED_HITS).coerceAtLeast(0)
        }
    }
}

private fun List<SharedGlobalSearchHit>.distinctByLocation(): List<SharedGlobalSearchHit> =
    groupBy { hit -> hit.chapterIndex to hit.pageIndex }
        .values
        .map { sameLocation -> sameLocation.maxByOrNull { hit -> hit.score } ?: sameLocation.first() }
        .sortedByDescending { it.score }
