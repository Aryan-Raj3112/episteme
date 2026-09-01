package com.aryan.reader.shared.reader

import com.aryan.reader.shared.ReaderLocator

/**
 * A TOC row after the common projection has applied search and expansion state.
 *
 * [originalIndex] is always an index into the effective (unfiltered) TOC.  Keeping that
 * identity on the projected row prevents a filtered or collapsed list from accidentally
 * navigating to a different chapter.
 */
data class ReaderTocProjectionEntry<T>(
    val originalIndex: Int,
    val entry: T,
    val isQueryMatch: Boolean,
    val isActive: Boolean,
    val hasChildren: Boolean
)

/** The state produced when the user asks the TOC to locate the current reading position. */
data class ReaderTocLocatePlan(
    val expandedEntryIndices: Set<Int>,
    val visibleOriginalIndex: Int?,
    val visibleIndex: Int?
)

/** Returns all entries that own at least one direct or nested child. */
fun <T> readerTocParentIndices(
    entries: List<T>,
    depthOf: (T) -> Int
): Set<Int> = entries.indices.filter { index ->
    entries.getOrNull(index + 1)?.let { depthOf(it) > depthOf(entries[index]) } == true
}.toSet()

/**
 * Finds the structural ancestors of [originalIndex] in a flattened, depth-labelled TOC.
 * This does not assume a maximum depth and tolerates skipped depth values from imperfect EPUBs.
 */
fun <T> readerTocAncestorIndices(
    entries: List<T>,
    originalIndex: Int,
    depthOf: (T) -> Int
): Set<Int> {
    val targetDepth = entries.getOrNull(originalIndex)?.let(depthOf) ?: return emptySet()
    val ancestors = linkedSetOf<Int>()
    var requiredDepth = targetDepth
    for (index in originalIndex - 1 downTo 0) {
        val entryDepth = depthOf(entries[index])
        if (entryDepth < requiredDepth) {
            ancestors += index
            requiredDepth = entryDepth
        }
        if (requiredDepth <= 0) break
    }
    return ancestors
}

/** Expands the ancestors needed for a locate action while preserving unrelated expansion. */
fun <T> readerTocExpandedForLocate(
    entries: List<T>,
    expandedEntryIndices: Set<Int>,
    activeOriginalIndex: Int?,
    depthOf: (T) -> Int
): Set<Int> = activeOriginalIndex
    ?.takeIf { it in entries.indices }
    ?.let { expandedEntryIndices + readerTocAncestorIndices(entries, it, depthOf) }
    ?: expandedEntryIndices

/** Toggles one parent without affecting expansion state for any other branch. */
fun <T> readerTocToggleExpansion(
    entries: List<T>,
    expandedEntryIndices: Set<Int>,
    originalIndex: Int,
    depthOf: (T) -> Int
): Set<Int> {
    if (originalIndex !in entries.indices) return expandedEntryIndices
    val hasChildren = entries.getOrNull(originalIndex + 1)?.let {
        depthOf(it) > depthOf(entries[originalIndex])
    } == true
    if (!hasChildren) return expandedEntryIndices
    return if (originalIndex in expandedEntryIndices) {
        expandedEntryIndices - originalIndex
    } else {
        expandedEntryIndices + originalIndex
    }
}

/**
 * Projects a flattened TOC using per-parent expansion state.  During search, matching rows and
 * their ancestors are retained so a nested result has enough context even when its parent was
 * collapsed before the search started.
 */
fun <T> projectReaderTocEntries(
    entries: List<T>,
    expandedEntryIndices: Set<Int>,
    query: String,
    activeOriginalIndex: Int? = null,
    labelOf: (T) -> String,
    depthOf: (T) -> Int
): List<ReaderTocProjectionEntry<T>> {
    if (entries.isEmpty()) return emptyList()

    val normalizedQuery = query.trim()
    val includedIndices: Set<Int>
    val matchingIndices: Set<Int>
    if (normalizedQuery.isBlank()) {
        includedIndices = entries.indices.filter { index ->
            readerTocAncestorIndices(entries, index, depthOf).all { it in expandedEntryIndices }
        }.toSet()
        matchingIndices = entries.indices.toSet()
    } else {
        val matches = entries.indices.filterTo(mutableSetOf()) { index ->
            labelOf(entries[index]).contains(normalizedQuery, ignoreCase = true)
        }
        matchingIndices = matches
        includedIndices = matches
            .flatMapTo(linkedSetOf()) { index ->
                readerTocAncestorIndices(entries, index, depthOf) + index
            }
    }

    return entries.indices
        .filter { it in includedIndices }
        .map { index ->
            ReaderTocProjectionEntry(
                originalIndex = index,
                entry = entries[index],
                isQueryMatch = index in matchingIndices,
                isActive = index == activeOriginalIndex,
                hasChildren = entries.getOrNull(index + 1)?.let {
                    depthOf(it) > depthOf(entries[index])
                } == true
            )
        }
}

/** Computes the row position that should be passed to a lazy list after Locate expands parents. */
fun <T> readerTocLocatePlan(
    entries: List<T>,
    expandedEntryIndices: Set<Int>,
    activeOriginalIndex: Int?,
    depthOf: (T) -> Int
): ReaderTocLocatePlan {
    val nextExpanded = readerTocExpandedForLocate(
        entries = entries,
        expandedEntryIndices = expandedEntryIndices,
        activeOriginalIndex = activeOriginalIndex,
        depthOf = depthOf
    )
    val visible = projectReaderTocEntries(
        entries = entries,
        expandedEntryIndices = nextExpanded,
        query = "",
        activeOriginalIndex = activeOriginalIndex,
        labelOf = { "" },
        depthOf = depthOf
    )
    val visibleIndex = visible.indexOfFirst { it.originalIndex == activeOriginalIndex }
        .takeIf { it >= 0 }
    return ReaderTocLocatePlan(
        expandedEntryIndices = nextExpanded,
        visibleOriginalIndex = activeOriginalIndex,
        visibleIndex = visibleIndex
    )
}

/**
 * Android-compatible effective TOC: books without a parsed navigation document still expose
 * every spine chapter so the drawer remains navigable instead of showing an empty state.
 */
fun SharedEpubBook.effectiveReaderTocEntries(): List<SharedEpubTocEntry> =
    tableOfContents.ifEmpty {
        chapters.map { chapter ->
            SharedEpubTocEntry(
                label = chapter.title,
                href = chapter.baseHref ?: chapter.id,
                fragmentId = null,
                depth = chapter.depth
            )
        }
    }

/**
 * Resolves the active row for native/WebView position callbacks.  A fragment supplied by the
 * WebView is preferred; native readers generally only report a chapter and therefore fall back
 * to the chapter's path/fragment and then to the chapter index.
 */
fun readerTocActiveIndex(
    entries: List<SharedEpubTocEntry>,
    book: SharedEpubBook,
    locator: ReaderLocator?,
    activeHref: String? = null,
    activeFragmentId: String? = null
): Int? {
    val chapter = locator?.chapterIndex?.let(book.chapters::getOrNull)
    val targetHref = activeHref ?: locator?.href ?: chapter?.baseHref
    val targetPath = targetHref?.readerTocNormalizedPath()?.takeIf(String::isNotBlank)
    val targetFragment = activeFragmentId ?: chapter?.fragmentId

    if (targetPath != null) {
        val exact = entries.indexOfFirst { entry ->
            readerTocPathsMatch(entry.href.readerTocNormalizedPath(), targetPath) &&
                (targetFragment == null || entry.fragmentId == targetFragment)
        }
        if (exact >= 0) return exact

        val samePath = entries.indexOfFirst {
            readerTocPathsMatch(it.href.readerTocNormalizedPath(), targetPath)
        }
        if (samePath >= 0) return samePath
    }

    val chapterIndex = locator?.chapterIndex
    if (chapterIndex != null) {
        val chapterPath = book.chapters.getOrNull(chapterIndex)?.baseHref?.readerTocNormalizedPath()
        val byChapter = entries.indexOfFirst { entry ->
            chapterPath != null && readerTocPathsMatch(entry.href.readerTocNormalizedPath(), chapterPath)
        }
        if (byChapter >= 0) return byChapter
    }
    return null
}

private fun String.readerTocNormalizedPath(): String =
    replace('\\', '/')
        .substringBefore('#')
        .substringBefore('?')
        .split('/')
        .filter { it.isNotBlank() && it != "." }
        .fold(ArrayDeque<String>()) { parts, part ->
            if (part == "..") {
                if (parts.isNotEmpty()) parts.removeLast()
            } else {
                parts.addLast(part)
            }
            parts
        }
        .joinToString("/")

private fun readerTocPathsMatch(left: String, right: String): Boolean =
    left == right || left.substringAfterLast('/') == right.substringAfterLast('/')
