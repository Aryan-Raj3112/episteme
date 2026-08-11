package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.SharedDocumentMetadata
import com.aryan.reader.shared.parseSharedDocumentXmlMetadata
import com.aryan.reader.shared.parseSharedFb2Metadata

internal val IosPresentationMetadataTypes = setOf(
    FileType.PDF, FileType.EPUB, FileType.MOBI, FileType.FB2, FileType.CBZ,
    FileType.DOCX, FileType.ODT, FileType.FODT, FileType.PPTX,
)

internal fun extractIosZipDocumentPresentation(book: BookItem, metadataPath: String): IosBookPresentation {
    val path = book.path.resolveIosEpubSourcePath() ?: return IosBookPresentation()
    val xml = IosZipEpubArchive(path).readText(metadataPath) ?: return IosBookPresentation()
    return parseSharedDocumentXmlMetadata(xml).toIosBookPresentation()
}

internal fun extractIosFlatDocumentPresentation(book: BookItem): IosBookPresentation {
    val path = book.path.resolveIosEpubSourcePath() ?: return IosBookPresentation()
    return parseSharedDocumentXmlMetadata(path.readIosFileBytes().decodeEpubText()).toIosBookPresentation()
}

internal fun extractIosFb2Presentation(book: BookItem): IosBookPresentation {
    val path = book.path.resolveIosEpubSourcePath() ?: return IosBookPresentation()
    return parseSharedFb2Metadata(path.readIosFileBytes().decodeEpubText()).toIosBookPresentation()
}

private fun SharedDocumentMetadata.toIosBookPresentation() = IosBookPresentation(
    title = title,
    author = author,
)
