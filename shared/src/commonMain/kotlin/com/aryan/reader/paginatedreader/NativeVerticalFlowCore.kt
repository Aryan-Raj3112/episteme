package com.aryan.reader.paginatedreader

/**
 * Generic vertical-flow core shared by both native stacks (B1 axis A).
 *
 * Android's paginated vertical flow (`ContentBlock` family) and shared's
 * semantic vertical flow (`SemanticBlock` family) build the same shape —
 * chapter boundary gaps, per-block keys, first-non-blank text choice and a
 * four-level locate cascade — over incompatible sealed hierarchies. The
 * hierarchies stay separate (merging them ripples through highlight paint
 * paths, the paginator and both renderers); this file owns only the
 * type-free parts both sides delegate to: key formats, boundary rule,
 * text-range aggregation, first-text choice and cascade ordering.
 *
 * Android remains the benchmark: every default here matches the Android
 * behavior the callers already had.
 */

/** Gap inserted between chapters; both stacks use the identical format. */
fun nativeVerticalGapKey(chapterIndex: Int): String = "chapter-$chapterIndex-gap"

/** Per-block item key; both stacks use the identical format. */
fun nativeVerticalBlockKey(chapterIndex: Int, ordinal: Int, blockIndex: Int): String =
    "chapter-$chapterIndex-block-$ordinal-$blockIndex"

fun nativeVerticalUnloadedKey(chapterIndex: Int): String = "chapter-$chapterIndex-unloaded"

fun nativeVerticalEmptyKey(chapterIndex: Int): String = "chapter-$chapterIndex-empty"

fun nativeVerticalPageKey(chapterIndex: Int, pageIndex: Int): String =
    "chapter-$chapterIndex-page-$pageIndex"

fun nativeVerticalSyntheticTextKey(chapterIndex: Int): String = "chapter-$chapterIndex-text"

/** A boundary gap precedes every chapter after the first. */
fun shouldInsertNativeVerticalGap(chapterOrdinal: Int): Boolean = chapterOrdinal > 0

/**
 * First text unit to anchor a location: the first non-blank text, else the
 * first unit, else null. Both stacks had this verbatim (Android's
 * `nativeVerticalNavigationTargetForBlock`, shared's
 * `firstNativeLocatorBlockPosition`).
 */
fun <T> pickNativeVerticalFirstTextUnit(
    units: List<T>,
    textOf: (T) -> String,
): T? = units.firstOrNull { textOf(it).isNotBlank() } ?: units.firstOrNull()

/**
 * Aggregates flattened text units into a single source range: min start to
 * max end. Both stacks flatten then aggregate; the only difference was what
 * each did with the range (weight vs page range). Returns null when empty.
 */
fun nativeVerticalTextRangeFromOffsets(offsets: List<Pair<Int, Int>>): Pair<Int, Int>? {
    if (offsets.isEmpty()) return null
    val start = offsets.minOf { it.first }
    val end = offsets.maxOf { it.first + it.second }
    return start to end.coerceAtLeast(start)
}

/**
 * Four-level locate cascade: block hit wins over char-offset hit wins over
 * page hit wins over chapter hit. Both stacks resolve in this order
 * (Android: exact CFI/offset → container → blockIndex → first-in-chapter;
 * shared: blockIndex → offset → pageIndex → chapterIndex); each caller
 * computes its own candidate indices and this combinator picks the first
 * live one so the ordering cannot drift between the two.
 *
 * Each candidate is an item index or null/negative when that level missed.
 */
fun resolveNativeVerticalFlowIndexInOrder(
    blockLevelIndex: Int?,
    offsetLevelIndex: Int?,
    pageLevelIndex: Int?,
    chapterLevelIndex: Int?,
): Int? = listOf(blockLevelIndex, offsetLevelIndex, pageLevelIndex, chapterLevelIndex)
    .firstOrNull { it != null && it >= 0 }
