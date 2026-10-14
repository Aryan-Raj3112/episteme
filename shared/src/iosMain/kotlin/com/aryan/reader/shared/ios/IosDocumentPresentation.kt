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

/**
 * Whether a book still needs a presentation-metadata pass.
 *
 * One definition, shared by the extraction loop and the queue diagnostics, so the
 * two can never disagree about what "pending" means.
 *
 * `seriesName` is deliberately absent. A book that belongs to no series has
 * nothing left to extract, so including it left *every* book permanently pending:
 * the pending set never drained, the effect re-keyed on unrelated library
 * changes, and `remainingPending` reported work that was never actionable.
 */
internal fun needsIosPresentationExtraction(book: BookItem): Boolean =
    book.path != null &&
        book.type in IosPresentationMetadataTypes &&
        (
            book.coverImagePath.isNullOrBlank() ||
                book.title.isNullOrBlank() ||
                book.title == book.displayName.substringBeforeLast('.', book.displayName)
            )

/**
 * Identity of "this extraction already ran" for one library row.
 *
 * The id alone is not enough: ids are content hashes, so a re-imported book
 * reuses the id of the copy it replaces while pointing at a different path. The
 * path is part of the key so that swap counts as new work, and callers also
 * prune keys for books that have left the library entirely.
 */
internal data class PresentationAttemptKey(
    val id: String,
    val path: String?,
) {
    companion object {
        fun of(book: BookItem): PresentationAttemptKey = PresentationAttemptKey(book.id, book.path)
    }
}

internal fun extractIosZipDocumentPresentation(book: BookItem, metadataPath: String): IosBookPresentation {
    val archive = openIosZipArchive(book.path) ?: return IosBookPresentation()
    val xml = archive.readText(metadataPath) ?: return IosBookPresentation()
    return parseSharedDocumentXmlMetadata(xml).toIosBookPresentation()
}

internal fun extractIosFlatDocumentPresentation(book: BookItem): IosBookPresentation {
    val bytes = readIosBookBytes(book.path) ?: return IosBookPresentation()
    return parseSharedDocumentXmlMetadata(bytes.decodeEpubText()).toIosBookPresentation()
}

internal fun extractIosFb2Presentation(book: BookItem): IosBookPresentation {
    val bytes = readIosBookBytes(book.path) ?: return IosBookPresentation()
    return parseSharedFb2Metadata(bytes.decodeEpubText()).toIosBookPresentation()
}

private fun SharedDocumentMetadata.toIosBookPresentation() = IosBookPresentation(
    title = title,
    author = author,
)
