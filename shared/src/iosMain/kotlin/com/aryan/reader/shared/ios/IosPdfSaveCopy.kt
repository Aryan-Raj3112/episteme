@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.SharedPdfExportMode
import com.aryan.reader.shared.pdf.SharedPdfExportSnapshot
import com.aryan.reader.shared.pdf.exportIosPdfAnnotations
import com.aryan.reader.shared.pdf.sharedPdfExportMode
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

internal sealed interface IosPdfSaveCopyPreparation {
    data class Ready(val book: BookItem) : IosPdfSaveCopyPreparation
    data class Unavailable(val message: String) : IosPdfSaveCopyPreparation
}

internal suspend fun prepareIosPdfSaveCopy(
    book: BookItem,
    password: String?,
    snapshot: SharedPdfExportSnapshot,
    exporter: suspend (String, String, String?, SharedPdfExportSnapshot) -> Boolean =
        ::exportIosPdfAnnotations,
): IosPdfSaveCopyPreparation {
    val sourcePath = book.path?.takeIf(NSFileManager.defaultManager::fileExistsAtPath)
        ?: return IosPdfSaveCopyPreparation.Unavailable("Unable to open ${book.displayName} for export.")
    return when (sharedPdfExportMode(snapshot)) {
        SharedPdfExportMode.ORIGINAL -> IosPdfSaveCopyPreparation.Ready(book)
        SharedPdfExportMode.UNSUPPORTED_VIRTUAL_PAGES -> IosPdfSaveCopyPreparation.Unavailable(
            "Save Copy does not yet support inserted PDF pages.",
        )
        SharedPdfExportMode.UNSUPPORTED_TEXT_CONTENT -> IosPdfSaveCopyPreparation.Unavailable(
            "Unable to prepare PDF text content for export.",
        )
        SharedPdfExportMode.ANNOTATED -> {
            val destination = "${NSTemporaryDirectory().trimEnd('/')}/reader-export-${NSUUID.UUID().UUIDString}.pdf"
            if (exporter(sourcePath, destination, password, snapshot)) {
                IosPdfSaveCopyPreparation.Ready(book.copy(path = destination))
            } else {
                NSFileManager.defaultManager.removeItemAtPath(destination, error = null)
                IosPdfSaveCopyPreparation.Unavailable("Unable to export annotations from ${book.displayName}.")
            }
        }
    }
}
