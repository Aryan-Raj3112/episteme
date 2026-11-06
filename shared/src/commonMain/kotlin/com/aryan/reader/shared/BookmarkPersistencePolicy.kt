package com.aryan.reader.shared

/**
 * Shared bookmark/highlight persistence policy for mobile readers (Android
 * benchmark, iOS parity).
 *
 * Bookmarks and highlights disappear when a reader restores a stale/empty
 * snapshot over good in-memory state and then persists the empty value.
 * Annotations survive because they reload from bookId-keyed sidecars with a
 * session guard; bookmarks and EPUB highlights historically used a one-shot
 * JSON snapshot with no guard.
 *
 * These pure helpers are the single source of truth for:
 * - treating blank/empty annotation JSON as equivalent (last-line-of-defense
 *   save guard),
 * - deciding when a late/stale snapshot may fill local state, and
 * - deciding when an empty local set is a real delete-all rather than a
 *   pre-load artifact.
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
 * Returns true when [snapshotJson] is allowed to replace local bookmark or
 * highlight state.
 *
 * - A reader with local user edits is authoritative: snapshots never overwrite
 *   it. This protects intentional "delete all" as well as unsaved edits racing
 *   a tab switch (the stale snapshot must not win).
 * - A fresh reader (no edits, nothing loaded) may be filled from a non-empty
 *   snapshot. This covers the async open path where the snapshot arrives after
 *   the reader already composed with null.
 * - An empty snapshot never wipes non-empty local state.
 */
fun shouldApplyAnnotationSnapshot(
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

/**
 * Whether persisting the current empty local annotation set should be skipped.
 *
 * Before the durable snapshot has loaded, an empty in-memory set is an artifact
 * (nothing was read yet) and persisting it wipes the stored annotations. Once
 * the reader has had non-empty local state (loaded from disk/prefs or authored
 * by the user), an empty set is a real delete-all and must persist — otherwise
 * the deleted entries reappear on the next open.
 *
 * [hasLocalState] is the session's "touched" flag: true once the reader loaded
 * a non-empty snapshot or saw non-empty local state. [localCount] is the live
 * entry count.
 */
fun shouldSkipEmptyAnnotationSave(hasLocalState: Boolean, localCount: Int): Boolean =
    !hasLocalState && localCount == 0
