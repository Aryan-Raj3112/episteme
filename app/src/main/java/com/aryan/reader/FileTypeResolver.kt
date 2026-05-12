package com.aryan.reader

import com.aryan.reader.shared.SharedFileCapabilities

internal fun resolveFileTypeFromName(fileName: String?): FileType? {
    return SharedFileCapabilities.resolveFileTypeForName(fileName)
}

internal fun resolveFileTypeFromMetadata(fileName: String?, mimeType: String?): FileType? {
    val normalizedMimeType = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
    return when (normalizedMimeType) {
        "application/vnd.oasis.opendocument.text" -> FileType.ODT
        "application/x-vnd.oasis.opendocument.text-flat-xml" -> FileType.FODT
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> FileType.DOCX
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> FileType.PPTX
        "application/zip", "application/vnd.comicbook+zip", "application/x-cbz" -> {
            when {
                fileName?.endsWith(".cbz", ignoreCase = true) == true -> FileType.CBZ
                fileName?.endsWith(".fb2.zip", ignoreCase = true) == true -> FileType.FB2
                else -> null
            }
        }
        "application/vnd.comicbook-rar", "application/x-cbr", "application/x-rar-compressed" -> {
            if (fileName?.endsWith(".cbr", ignoreCase = true) == true) FileType.CBR else null
        }
        "application/x-cb7", "application/x-7z-compressed" -> {
            if (fileName?.endsWith(".cb7", ignoreCase = true) == true) FileType.CB7 else null
        }
        "application/pdf" -> FileType.PDF
        "application/epub+zip" -> FileType.EPUB
        "application/x-fictionbook+xml", "application/x-zip-compressed-fb2" -> FileType.FB2
        "application/x-mobipocket-ebook", "application/vnd.amazon.ebook", "application/vnd.amazon.mobi8-ebook" -> FileType.MOBI
        "text/markdown", "text/x-markdown" -> FileType.MD
        "text/html", "application/xhtml+xml" -> FileType.HTML
        "text/csv", "text/comma-separated-values", "text/tab-separated-values",
        "application/json", "application/xml", "text/xml",
        "text/x-java-source", "text/x-python", "text/x-kotlin",
        "text/javascript", "application/javascript",
        "text/x-c", "text/x-c++", "text/x-csharp", "text/x-ruby", "text/x-go", "text/x-log" -> FileType.HTML
        "text/plain" -> resolveFileTypeFromName(fileName) ?: FileType.TXT
        else -> resolveFileTypeFromName(fileName)
    }
}

internal fun isCodeOrDataFileName(fileName: String): Boolean {
    return SharedFileCapabilities.isCodeOrDataFileName(fileName)
}

internal fun isManualOnlyReaderFileName(fileName: String?): Boolean {
    return SharedFileCapabilities.isManualOnlyReaderFileName(fileName)
}

internal fun isManualOnlyReaderMimeType(mimeType: String?): Boolean {
    return SharedFileCapabilities.isManualOnlyReaderMimeType(mimeType)
}

internal fun isLocalFolderSyncEligibleFile(name: String, mimeType: String?): Boolean {
    return SharedFileCapabilities.isLocalFolderSyncEligibleFile(name, mimeType)
}

internal fun resolveFileExtensionSuffixFromName(fileName: String?): String? {
    return SharedFileCapabilities.fileExtensionSuffixForName(fileName)
}
