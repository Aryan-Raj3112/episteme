package com.aryan.reader.shared.ios

/**
 * Native presentation selected for each iOS file-transfer intent.
 *
 * A library save/export is a user-selected destination operation, so it must
 * use the document picker. Sharing remains a share-sheet operation, and PDF
 * Save Copy deliberately follows the same picker route.
 */
internal enum class IosFileTransferIntent {
    LIBRARY_EXPORT,
    ANNOTATION_EXPORT,
    PDF_SAVE_COPY,
    USER_SHARE,
    DIAGNOSTIC_EXPORT,
}

internal enum class IosFileTransferPresentation {
    DOCUMENT_PICKER_EXPORT,
    SHARE_SHEET,
}

internal fun iosFileTransferPresentation(
    intent: IosFileTransferIntent,
): IosFileTransferPresentation = when (intent) {
    IosFileTransferIntent.LIBRARY_EXPORT,
    IosFileTransferIntent.ANNOTATION_EXPORT,
    IosFileTransferIntent.PDF_SAVE_COPY,
    -> IosFileTransferPresentation.DOCUMENT_PICKER_EXPORT

    IosFileTransferIntent.USER_SHARE,
    IosFileTransferIntent.DIAGNOSTIC_EXPORT,
    -> IosFileTransferPresentation.SHARE_SHEET
}
