package com.aryan.reader.shared.ios

import com.aryan.reader.shared.LOCAL_FOLDER_SCAN_LOG_TAG
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

/**
 * Records what happened when the user opened a linked-folder book.
 *
 * The library preflight treats an unresolvable ref as "present" on purpose —
 * `mobileBookOpenPreflightAction` must not remove a book it cannot prove is
 * gone — so before this probe the open-time failure (bookmark gone, scope
 * denied, file missing) left no trace in the exported diagnostics. One line
 * per open, under the folder-scan tag; plain paths and non-provider refs are
 * ignored so this stays a linked-folder diagnostic.
 */
internal fun logIosFolderBookOpenProbe(bookId: String, displayName: String, pathOrRef: String?) {
    val value = pathOrRef?.trim()?.takeIf { it.isNotBlank() } ?: return
    if (!SharedIosBookSourceRef.isProviderRef(value)) return
    val name = displayName.replace('\n', ' ').take(80)
    val decoded = SharedIosBookSourceRef.decode(value)
    if (decoded == null) {
        IosDiagnosticLogStore.record(
            LOCAL_FOLDER_SCAN_LOG_TAG,
            "open.probe id=$bookId name=$name decode_failed ref=$value",
        )
        return
    }
    val outcome = withIosFolderBookPath(value) { resolved ->
        "resolved=$resolved exists=${NSFileManager.defaultManager.fileExistsAtPath(resolved)}"
    }
    val detail = outcome ?: "scope_or_resolve_failed ref=$value"
    IosDiagnosticLogStore.record(
        LOCAL_FOLDER_SCAN_LOG_TAG,
        "open.probe id=$bookId name=$name folder=${decoded.folderName} $detail",
    )
}
