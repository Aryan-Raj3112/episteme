package com.aryan.reader.paginatedreader

/**
 * Single source for EPUB CFI path / offset arithmetic (parity item B1).
 *
 * Android's `CfiUtils` and shared's `sharedNativeCfiPointOrNull` /
 * `sharedNativeCfiPathStrictlyBetween` were two implementations of the same question -- "where in
 * this chapter is this CFI?" -- and they disagreed. The bodies here are Android's, moved verbatim,
 * and both platforms now delegate, so the two cannot drift again.
 *
 * The load-bearing detail is that [readerCfiPath] and [readerCfiOffset] are **independent**: a CFI
 * whose offset suffix is absent, empty or non-numeric is still a usable point at offset 0. Shared
 * used to reject such a point outright, which silently dropped the whole highlight on iOS while
 * Android rendered it from the start of the block.
 */

/** Everything before the first `:` — the whole string when there is no `:` at all. */
fun readerCfiPath(cfi: String): String = cfi.substringBefore(':')

/** Offset after the first `:`, or 0 when absent, empty or non-numeric. */
fun readerCfiOffset(cfi: String): Int = cfi.substringAfter(':', "0").toIntOrNull() ?: 0

/** Offset after the first `:`, or null when absent, empty or non-numeric. */
fun readerCfiOffsetOrNull(cfi: String): Int? = cfi.substringAfter(':', "").toIntOrNull()

/**
 * The numeric step indices of [cfi]'s path, or null when the path is empty or has a non-numeric
 * segment (`/4/nav`). Callers use the null to mean "not a comparable CFI position".
 */
fun readerCfiPathParts(cfi: String): List<Int>? {
    val segments = readerCfiPath(cfi).split('/').filter { it.isNotEmpty() }
    if (segments.isEmpty()) return null
    return segments.map { it.toIntOrNull() ?: return null }
}

/** Lexicographic comparison of two parsed CFI paths; a shorter prefix sorts first. */
fun readerCompareCfiPathParts(first: List<Int>, second: List<Int>): Int {
    val length = minOf(first.size, second.size)
    for (index in 0 until length) {
        val comparison = first[index].compareTo(second[index])
        if (comparison != 0) return comparison
    }
    return first.size.compareTo(second.size)
}

/**
 * True when [candidate]'s path sits strictly between [start] and [end], used to decide whether a
 * block is an *intermediate* block of a multi-part highlight. Any of the three CFIs being
 * uncomparable (null path parts) answers false rather than guessing.
 */
fun readerCfiPathStrictlyBetween(candidate: String, start: String, end: String): Boolean {
    val candidateParts = readerCfiPathParts(candidate) ?: return false
    val startParts = readerCfiPathParts(start) ?: return false
    val endParts = readerCfiPathParts(end) ?: return false
    return readerCompareCfiPathParts(candidateParts, startParts) > 0 &&
        readerCompareCfiPathParts(candidateParts, endParts) < 0
}

/**
 * Full CFI ordering: path parts first, then the offset as a tiebreak. Two CFIs with identical
 * paths but different offsets are distinct positions, which is why this cannot be expressed in
 * terms of [readerCompareCfiPathParts] alone.
 */
fun readerCompareCfi(cfi1: String, cfi2: String): Int {
    val path1 = readerCfiPath(cfi1)
    val path2 = readerCfiPath(cfi2)

    val parts1 = path1.split('/').filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
    val parts2 = path2.split('/').filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }

    val length = minOf(parts1.size, parts2.size)
    for (i in 0 until length) {
        val cmp = parts1[i].compareTo(parts2[i])
        if (cmp != 0) return cmp
    }

    if (parts1.size != parts2.size) {
        return parts1.size.compareTo(parts2.size)
    }

    return readerCfiOffset(cfi1).compareTo(readerCfiOffset(cfi2))
}

/** A parsed `path` + `offset` CFI point. */
data class ReaderCfiPoint(
    val path: String,
    val offset: Int
)

/**
 * Parse a single CFI point, or null when its path is not a `/`-rooted CFI path. A malformed offset
 * suffix does **not** reject the point — it resolves to offset 0, matching [readerCfiOffset].
 * This is what Android's highlight mapper does end to end: it matches on paths via
 * `CfiUtils.getPath`, then falls back to `CfiUtils.getOffset` (which is 0) when `getOffsetOrNull`
 * returned null.
 */
fun readerCfiPointOrNull(cfi: String): ReaderCfiPoint? {
    val path = readerCfiPath(cfi).takeIf { it.startsWith("/") } ?: return null
    return ReaderCfiPoint(path, readerCfiOffset(cfi))
}