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
 * Two shapes are accepted:
 *  - a plain absolute filesystem path, for app-managed books;
 *  - a `file://` URL, which is what a linked-folder book resolves to once its
 *    security-scoped bookmark has been resolved.
 *
 * A `ios-folder-book://` ref is deliberately *not* resolvable here. It needs a
 * live security scope and a bookmark lookup, which is `IosFolderBookScope`'s
 * job; a ref reaching this function means the scope was never acquired, and
 * guessing a path from it would read the wrong file.
 */
internal fun String?.resolveIosReadablePath(): String? {
    val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (SharedIosBookSourceRef.isProviderRef(value)) return null
    if (!value.startsWith("file://")) return value
    return NSURL.URLWithString(value)?.path ?: value.removePrefix("file://")
}

/**
 * Whether a book's stored `path` currently resolves to a readable file.
 *
 * A linked-folder ref is reported as unavailable here rather than guessed at:
 * whether it resolves depends on a security scope this layer cannot acquire, so
 * the honest answer is "unknown, do not treat as missing". Getting this wrong
 * is destructive, because a book whose path looks absent is removed from the
 * library by the folder-sync reconciliation.
 */
internal fun String?.isIosReadableBookPath(): Boolean {
    if (SharedIosBookSourceRef.isProviderRef(this)) {
        // A ref's readability depends on a security scope this layer cannot
        // acquire on its own. Report unavailable rather than guessing.
        return resolveIosFolderBookPath(this) != null
    }
    val resolved = resolveIosReadablePath() ?: return false
    return NSFileManager.defaultManager.fileExistsAtPath(resolved)
}
