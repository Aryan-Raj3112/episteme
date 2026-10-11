package com.aryan.reader.shared

/**
 * Shared bookmark persistence policy for mobile readers (Android benchmark, iOS parity).
 *
 * Bookmarks disappear when a reader tab switch restores a stale/empty snapshot over
 * good in-memory state and then persists the empty value. Annotations survive because
 * they reload from bookId-keyed sidecars with a session guard; bookmarks historically
 * used a one-shot `initialBookmarksJson` snapshot with no guard.
 *
 * These pure helpers are the single source of truth for:
 * - treating blank/empty bookmark JSON as equivalent (last-line-of-defense save guard), and
 * - deciding when a late/stale snapshot may fill local bookmark state.
 */
fun isBookmarkJsonEmpty(json: String?): Boolean {
    val normalized = json?.trim().orEmpty()
    return normalized.isEmpty() || normalized == "[]"
}

fun bookmarkJsonEquivalentForNoop(existing: String?, incoming: String): Boolean {
    val existingNormalized = existing?.trim().orEmpty()
    val incomingNormalized = incoming.trim()
    if (existingNormalized == incomingNormalized) return true
    return isBookmarkJsonEmpty(existingNormalized) && isBookmarkJsonEmpty(incomingNormalized)
}

/**
 * Whether a bookmark save should be skipped.
 *
 * Covers both redundant writes (equivalent payloads) and destructive ones: a
 * stale empty snapshot must never overwrite persisted bookmarks (#556). An
 * intentional delete-all still persists: once the empty state is stored,
 * follow-up empty saves are equivalent noops.
 */
fun shouldSkipBookmarkSave(existing: String?, incoming: String): Boolean {
    if (bookmarkJsonEquivalentForNoop(existing, incoming)) return true
    return isBookmarkJsonEmpty(incoming) && !isBookmarkJsonEmpty(existing)
}

/**
 * Returns true when [snapshotJson] is allowed to replace local bookmark state.
 *
 * - A book with local user edits is authoritative: snapshots never overwrite it.
 *   This protects intentional "delete all bookmarks" as well as unsaved toggles
 *   racing a tab switch (the stale snapshot must not win).
 * - A fresh book (no edits, nothing loaded) may be filled from a non-empty snapshot.
 *   This covers the async open path where `initialBookmarksJson` arrives after the
 *   reader already composed with null.
 * - An empty snapshot never wipes non-empty local state.
 */
fun shouldApplyBookmarkSnapshot(
    hasLocalEdits: Boolean,
    localCount: Int,
    snapshotJson: String?,
    snapshotCount: Int,
): Boolean {
    if (hasLocalEdits) return false
    if (isBookmarkJsonEmpty(snapshotJson)) return false
    if (localCount == 0 && snapshotCount > 0) return true
    // Non-empty snapshot with no local edits restores the durable record,
    // e.g. returning to a tab whose in-memory state was dropped.
    if (snapshotCount > 0) return true
    return false
}
