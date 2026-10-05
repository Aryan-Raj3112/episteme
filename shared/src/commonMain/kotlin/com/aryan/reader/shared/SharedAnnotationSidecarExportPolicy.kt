package com.aryan.reader.shared

/**
 * What a device should publish for one book's folder annotation sidecar.
 *
 * Folder annotations sync through a single sidecar file per book, merged by
 * last-writer-wins on a timestamp plus per-annotation tombstones. That makes
 * the *timestamp* of an export load-bearing: a device that has not imported its
 * peer's annotations yet has no local payload, and if it stamps that empty
 * state with wall clock it publishes "this book has no annotations, and that is
 * newer than yours", which erases the peer's real annotations on the next
 * merge.
 *
 * This was observed live: one device wrote a 31-byte empty annotation sidecar
 * moments before writing its real one, and the peer's highlights never arrived.
 *
 * The rule these helpers encode:
 *  - a real local edit is stamped with wall clock, because the user just made it
 *  - a clear may only outrank the snapshot it is clearing, so it is derived
 *    from that snapshot alone
 *  - with neither local annotations nor a prior sidecar there is nothing
 *    truthful to publish, so nothing is published
 */
sealed interface SharedAnnotationSidecarExportDecision {
    /** Publish [SharedPdfAnnotationSidecarExportPlan]'s payload with its timestamp. */
    data class Write(val plan: SharedPdfAnnotationSidecarExportPlan) : SharedAnnotationSidecarExportDecision

    /** Nothing to publish. The caller must not write a sidecar or burn a revision. */
    data object Skip : SharedAnnotationSidecarExportDecision
}

data class SharedPdfAnnotationSidecarExportPlan(
    /** Timestamp to record on the sidecar. */
    val timestamp: Long,
    /** True when this export is a tombstoning clear rather than a payload. */
    val isClear: Boolean,
)

/**
 * Decide what to publish for one book's annotation sidecar.
 *
 * @param hasLocalPayload whether this device holds any annotation artifact for
 *   the book. False for a device that simply has not imported yet, which is why
 *   it must not be treated as a deletion.
 * @param newestLocalArtifactTimestamp the newest local artifact mtime, used only
 *   when there is a payload to stamp.
 * @param priorSidecarTimestamp timestamp of the sidecar already in local folder
 *   storage, or 0 when none exists. A clear is only ever derived from this.
 * @param now current wall clock, injected so the rule is testable.
 */
fun decideSharedAnnotationSidecarExport(
    hasLocalPayload: Boolean,
    newestLocalArtifactTimestamp: Long,
    priorSidecarTimestamp: Long,
    now: Long,
): SharedAnnotationSidecarExportDecision {
    if (hasLocalPayload) {
        return SharedAnnotationSidecarExportDecision.Write(
            SharedPdfAnnotationSidecarExportPlan(
                // Wall clock is correct here: the user just edited. The
                // artifact mtime is included so a file touched by an import
                // cannot regress the timestamp below its own content.
                timestamp = maxOf(newestLocalArtifactTimestamp, now),
                isClear = false,
            ),
        )
    }
    if (priorSidecarTimestamp <= 0L) {
        // No annotations and nothing to clear. Publishing an empty sidecar here
        // would assert an absence this device has no basis for.
        return SharedAnnotationSidecarExportDecision.Skip
    }
    // A clear must outrank the snapshot it clears, otherwise the merge treats
    // the two as peers and keeps the annotations. Saturating at MAX_VALUE
    // preserves the existing "permanently cleared" signal.
    val clearTimestamp =
        if (priorSidecarTimestamp == Long.MAX_VALUE) Long.MAX_VALUE
        else priorSidecarTimestamp + 1L
    return SharedAnnotationSidecarExportDecision.Write(
        SharedPdfAnnotationSidecarExportPlan(
            timestamp = clearTimestamp,
            isClear = true,
        ),
    )
}
