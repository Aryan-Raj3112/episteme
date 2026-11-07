package com.aryan.reader.shared

/**
 * Whole-set JSON columns (bookmarks, highlights) and the annotation sidecar
 * travel through writers that cannot distinguish "this device has nothing to
 * share" from "the user cleared everything". A snapshot with zero entries is
 * serialized as an empty array ("[]"), and the folder-metadata v2 wire format
 * always writes the `bookmarksJson` / `highlightsJson` keys, even when their
 * value is null.
 *
 * Applying an empty marker to an existing non-empty payload destroys the data
 * without any user action, so callers merging two candidates use these helpers
 * to keep a payload that actually carries entries.
 *
 * Deliberate trade-off: "user deleted all entries and that fact should
 * propagate to peers" is not distinguishable from "this device has nothing to
 * share", so the empty marker never wins against a non-empty candidate.
 * Surviving a delete-all sync is far cheaper than silently erasing another
 * device's annotations; explicit clear paths must go through a dedicated
 * marker (e.g. a deletion timestamp), not through absence.
 */

/**
 * True when [json] carries no entries: null, blank, an empty array or object,
 * or the literal `null`. Whitespace (including between the empty delimiters) is
 * ignored so "[ ]" and "" both match. Scans the existing string without
 * allocating a whitespace-stripped copy, since these payloads can carry
 * thousands of entries.
 */
fun isEmptyJsonPayload(json: String?): Boolean {
    if (json == null) return true
    var start = 0
    var end = json.length
    while (start < end && json[start].isWhitespace()) start++
    while (end > start && json[end - 1].isWhitespace()) end--
    if (start == end) return true
    val first = json[start]
    if (first == 'n') {
        return end - start == 4 &&
            json[start] == 'n' &&
            json[start + 1] == 'u' &&
            json[start + 2] == 'l' &&
            json[start + 3] == 'l'
    }
    val close = when (first) {
        '[' -> ']'
        '{' -> '}'
        else -> return false
    }
    if (json[end - 1] != close) return false
    for (index in start + 1 until end - 1) {
        if (!json[index].isWhitespace()) return false
    }
    return true
}

/**
 * Selects the payload to keep between a [preferred] candidate and a [fallback]:
 * [preferred] when it carries entries, otherwise [fallback] (which may itself
 * be null or empty). Never returns an empty marker when a non-empty fallback
 * exists.
 */
fun keepNonEmptyJson(preferred: String?, fallback: String?): String? =
    if (isEmptyJsonPayload(preferred)) fallback else preferred
