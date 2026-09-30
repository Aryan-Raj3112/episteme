package com.aryan.reader.shared.ios

import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

/**
 * Resolves whatever a book stores in `path` into a filesystem path that
 * PDFium, `NSFileManager` and `Data` can actually read.
 *
 * This was previously duplicated four times (`resolvedIosPdfPath` in
 * `SharedMobilePdfRenderer`, `SharedMobilePdfSearch` and
 * `IosPdfTextSelectionBackend`, plus `resolveIosPdfPath` in `IosPdfOcrVision`).
 * They were identical, which meant every future fix had to be made four times
 * and one of them was missed when the EPUB helper was left behind.
 *
 * Three shapes are accepted:
 *  - a plain absolute filesystem path, for app-managed books;
 *  - a `file://` URL, which is what a linked-folder book resolves to once its
 *    security-scoped bookmark has been resolved;
 *  - a `ios-folder-book://` ref, resolved through the bookmark via
 *    [resolveIosFolderBookPath].
 *
 * A ref that cannot be resolved returns null rather than a guess. Callers treat
 * null as "cannot read this", which is the safe direction: the alternative —
 * falling back to a basename match under `Imports/` — can silently read a
 * different book.
 */
internal fun String?.resolveIosReadablePath(): String? {
    val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (SharedIosBookSourceRef.isProviderRef(value)) {
        return resolveIosFolderBookPath(value)
    }
    if (!value.startsWith("file://")) return value
    return NSURL.URLWithString(value)?.path ?: value.removePrefix("file://")
}

/**
 * Whether a book's stored `path` currently resolves to a readable file.
 *
 * A ref that cannot be resolved right now (its scope is not held, or the
 * folder is gone) is reported unavailable. Getting this wrong is destructive in
 * one direction and merely unhelpful in the other, so callers must not pass
 * `false` into a "the file is definitely gone" branch: see
 * `mobileBookOpenPreflightAction`, which removes the book from the library.
 */
internal fun String?.isIosReadableBookPath(): Boolean {
    val resolved = resolveIosReadablePath() ?: return false
    return NSFileManager.defaultManager.fileExistsAtPath(resolved)
}
