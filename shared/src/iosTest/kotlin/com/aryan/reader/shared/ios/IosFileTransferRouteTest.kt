package com.aryan.reader.shared.ios

import kotlin.test.Test
import kotlin.test.assertEquals

class IosFileTransferRouteTest {
    @Test
    fun libraryAndAnnotationExportsUseTheDocumentPicker() {
        assertEquals(
            IosFileTransferPresentation.DOCUMENT_PICKER_EXPORT,
            iosFileTransferPresentation(IosFileTransferIntent.LIBRARY_EXPORT),
        )
        assertEquals(
            IosFileTransferPresentation.DOCUMENT_PICKER_EXPORT,
            iosFileTransferPresentation(IosFileTransferIntent.ANNOTATION_EXPORT),
        )
    }

    @Test
    fun sharingAndDiagnosticsKeepTheShareSheet() {
        assertEquals(
            IosFileTransferPresentation.SHARE_SHEET,
            iosFileTransferPresentation(IosFileTransferIntent.USER_SHARE),
        )
        assertEquals(
            IosFileTransferPresentation.SHARE_SHEET,
            iosFileTransferPresentation(IosFileTransferIntent.DIAGNOSTIC_EXPORT),
        )
    }

    @Test
    fun pdfSaveCopyUsesTheDocumentPicker() {
        assertEquals(
            IosFileTransferPresentation.DOCUMENT_PICKER_EXPORT,
            iosFileTransferPresentation(IosFileTransferIntent.PDF_SAVE_COPY),
        )
    }
}
