@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.SharedPdfExportMode
import com.aryan.reader.shared.pdf.SharedPdfExportSnapshot
import com.aryan.reader.shared.pdf.exportIosPdfAnnotations
import com.aryan.reader.shared.pdf.sharedPdfExportMode
import platform.Foundation.NSFileManager

internal sealed interface IosPdfSaveCopyPreparation {
    data class Ready(val book: BookItem) : IosPdfSaveCopyPreparation
    data class Unavailable(val message: String) : IosPdfSaveCopyPreparation
}

internal suspend fun prepareIosPdfSaveCopy(
    book: BookItem,
    password: String?,
    snapshot: SharedPdfExportSnapshot,
    forceOriginal: Boolean = false,
    exporter: suspend (String, String, String?, SharedPdfExportSnapshot) -> Boolean =
        ::exportIosPdfAnnotations,
): IosPdfSaveCopyPreparation {
    val sourcePath = book.path?.takeIf(NSFileManager.defaultManager::fileExistsAtPath)
        ?: return IosPdfSaveCopyPreparation.Unavailable("Unable to open ${book.displayName} for export.")
    // The reader host launches this without a catch (scope.launch): never throw, always return
    // Unavailable so the user sees a message instead of the format dialog silently dismissing.
    return try {
        prepareStagedCopy(book, sourcePath, password, snapshot, forceOriginal, exporter)
    } catch (_: Throwable) {
        IosPdfSaveCopyPreparation.Unavailable("Unable to export annotations from ${book.displayName}.")
    }
}

private suspend fun prepareStagedCopy(
    book: BookItem,
    sourcePath: String,
    password: String?,
    snapshot: SharedPdfExportSnapshot,
    forceOriginal: Boolean,
    exporter: suspend (String, String, String?, SharedPdfExportSnapshot) -> Boolean,
): IosPdfSaveCopyPreparation {
    val mode = if (forceOriginal) SharedPdfExportMode.ORIGINAL else sharedPdfExportMode(snapshot)
    return when (mode) {
        SharedPdfExportMode.UNSUPPORTED_VIRTUAL_PAGES -> IosPdfSaveCopyPreparation.Unavailable(
            "Save Copy does not yet support inserted PDF pages.",
        )
        SharedPdfExportMode.UNSUPPORTED_TEXT_CONTENT -> IosPdfSaveCopyPreparation.Unavailable(
            "Unable to prepare PDF text content for export.",
        )
        SharedPdfExportMode.ORIGINAL -> {
            // Android benchmark (MainViewModel.sharePdf/includeAnnotations=false): even the
            // original is staged to a suggested filename, so the share sheet / document picker
            // never exposes internal library paths.
            val artifact = IosShareArtifactManager.prepare(book.displayName, isAnnotated = false)
            if (NSFileManager.defaultManager.copyItemAtPath(sourcePath, toPath = artifact.path, error = null)) {
                IosPdfSaveCopyPreparation.Ready(book.copy(path = artifact.path))
            } else {
                IosShareArtifactManager.discard(artifact)
                IosPdfSaveCopyPreparation.Unavailable("Unable to export annotations from ${book.displayName}.")
            }
        }
        SharedPdfExportMode.ANNOTATED -> {
            // Android benchmark (PdfViewerScreen.getSuggestedFilename): annotated copies carry
            // the "_annotated" marker plus a short id.
            val artifact = IosShareArtifactManager.prepare(book.displayName, isAnnotated = true)
            if (exporter(sourcePath, artifact.path, password, snapshot)) {
                IosPdfSaveCopyPreparation.Ready(book.copy(path = artifact.path))
            } else {
                IosShareArtifactManager.discard(artifact)
                IosPdfSaveCopyPreparation.Unavailable("Unable to export annotations from ${book.displayName}.")
            }
        }
    }
}
