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
 *
 * A provider-ref result is only readable while the caller holds the folder's
 * scope: iOS revokes access to a resolved URL when
 * `stopAccessingSecurityScopedResource` runs, and path-based `stat` follows the
 * same rule. Read the returned path inside [withResolvedIosBookPath], or wrap
 * the read in [acquireScopeForBookPath].
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
 * A provider ref is stat'ed with its folder scope held — the check the library
 * depends on to decide `isAvailable`. Doing the stat after the scope was
 * released reported every linked-folder book as missing on a real device while
 * passing in the simulator, which does not enforce the sandbox.
 *
 * A ref that still cannot be resolved (its bookmark is gone, or the folder was
 * removed) is reported unavailable. Getting this wrong is destructive in one
 * direction and merely unhelpful in the other, so callers must not pass
 * `false` into a "the file is definitely gone" branch: see
 * `mobileBookOpenPreflightAction`, which removes the book from the library.
 */
internal fun String?.isIosReadableBookPath(): Boolean {
    val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return false
    if (SharedIosBookSourceRef.isProviderRef(value)) {
        return withIosFolderBookPath(value) { resolved ->
            NSFileManager.defaultManager.fileExistsAtPath(resolved)
        } == true
    }
    val resolved = value.resolveIosReadablePath() ?: return false
    return NSFileManager.defaultManager.fileExistsAtPath(resolved)
}
