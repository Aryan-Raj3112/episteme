package com.aryan.reader.shared.pdf

const val FOLDER_ANNOTATION_EXPORT_QUIET_MILLIS = 2_000L
const val FOLDER_ANNOTATION_EXPORT_MAX_DIRTY_MILLIS = 10_000L

/** Coalesces edits but guarantees continuous editing cannot postpone an export forever. */
fun folderAnnotationExportDelayMillis(
    dirtySinceMillis: Long,
    nowMillis: Long,
    immediate: Boolean,
): Long {
    if (immediate) return 0L
    val dirtyAge = (nowMillis - dirtySinceMillis).coerceAtLeast(0L)
    val remainingMaximum = (FOLDER_ANNOTATION_EXPORT_MAX_DIRTY_MILLIS - dirtyAge).coerceAtLeast(0L)
    return minOf(FOLDER_ANNOTATION_EXPORT_QUIET_MILLIS, remainingMaximum)
}
