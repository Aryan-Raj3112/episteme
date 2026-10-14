package com.aryan.reader.shared.reader

/**
 * Which spine item narrates which reader chapter.
 *
 * The reader's chapter list is its own re-flow — split at TOC fragments, so the reference book has
 * 159 chapters for 156 spine items — while overlays are linked to *spine items*. Matching by content
 * path is the only identity the two share, and the index's
 * [SharedMediaOverlayIndex.contentPathBySpineItem] is what makes it exact rather than a guess at
 * the overlay's own `epub:textref`.
 *
 * A chapter whose path matches no spine item is absent, which is the correct answer: it has no
 * source document and therefore no narration.
 *
 * Takes content paths rather than chapters because the platforms' chapter types share no shape,
 * and this is the one fact about a chapter that all of them — reader, Android Listen, iOS Listen —
 * have. Lives in common code for the same reason: every surface must map chapters identically.
 */
fun sharedMediaOverlaySpineItemIndexByChapter(
    chapterContentPaths: List<String>,
    overlayIndex: SharedMediaOverlayIndex
): Map<Int, Int> {
    if (chapterContentPaths.isEmpty() || overlayIndex.contentPathBySpineItem.isEmpty()) return emptyMap()
    val spineByPath = HashMap<String, Int>(overlayIndex.contentPathBySpineItem.size)
    overlayIndex.contentPathBySpineItem.forEach { (spineItemIndex, path) ->
        val key = normalizeSharedMediaOverlayPath(path)
        // First writer wins, so a book whose spine names the same document twice still resolves to
        // the *earlier* item: that is the one a reader's re-flow started from.
        if (key.isNotEmpty() && !spineByPath.containsKey(key)) spineByPath[key] = spineItemIndex
    }
    if (spineByPath.isEmpty()) return emptyMap()
    val byChapter = HashMap<Int, Int>(chapterContentPaths.size)
    chapterContentPaths.forEachIndexed { chapterIndex, path ->
        spineByPath[normalizeSharedMediaOverlayPath(path)]?.let { byChapter[chapterIndex] = it }
    }
    return byChapter
}

/**
 * The book's spine items in reading order, deduplicated.
 *
 * Reads the chapter list rather than the spine so a document split into several reader chapters
 * contributes one entry, and a chapter with no spine item — a cover page the reader synthesises —
 * contributes none. This is what [sharedMediaOverlaySpineItemsAfter] walks to find the next narrated
 * chapter, so getting the order wrong is heard as narration that skips or repeats a chapter.
 */
fun sharedMediaOverlaySpineItemsInReadingOrder(
    chapterCount: Int,
    spineItemIndexByChapter: Map<Int, Int>
): List<Int> {
    if (chapterCount <= 0) return emptyList()
    val ordered = LinkedHashSet<Int>(chapterCount)
    for (chapterIndex in 0 until chapterCount) {
        spineItemIndexByChapter[chapterIndex]?.let(ordered::add)
    }
    return ordered.toList()
}

/**
 * Two paths that name the same document compare equal here.
 *
 * A spine item's `absPath` and a chapter's own path routinely differ by a leading slash, a fragment,
 * or a query string, and a mapping that missed on those would drop a chapter's narration while still
 * looking correct for every other chapter in the book.
 */
fun normalizeSharedMediaOverlayPath(path: String): String =
    path.substringBefore('#').substringBefore('?').trim().trimStart('/')
