package com.aryan.reader

private val codeOrDataExtensions = setOf(
    "csv",
    "tsv",
    "json",
    "xml",
    "log",
    "java",
    "kt",
    "py",
    "js",
    "cpp",
    "c",
    "cs",
    "rb",
    "go"
)

internal fun resolveFileTypeFromName(fileName: String?): FileType? {
    val lowerName = fileName?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    val effectiveName = lowerName.withTransparentTextSuffix()

    return when {
        effectiveName.endsWith(".cbz") -> FileType.CBZ
        effectiveName.endsWith(".cbr") -> FileType.CBR
        effectiveName.endsWith(".cb7") -> FileType.CB7
        effectiveName.endsWith(".pdf") -> FileType.PDF
        effectiveName.endsWith(".epub") -> FileType.EPUB
        effectiveName.endsWith(".mobi") || effectiveName.endsWith(".azw3") || effectiveName.endsWith(".prc") -> FileType.MOBI
        effectiveName.endsWith(".fb2") || effectiveName.endsWith(".fb2.zip") -> FileType.FB2
        effectiveName.endsWith(".md") || effectiveName.endsWith(".markdown") -> FileType.MD
        effectiveName.endsWith(".html") || effectiveName.endsWith(".xhtml") || effectiveName.endsWith(".htm") -> FileType.HTML
        effectiveName.endsWith(".docx") -> FileType.DOCX
        effectiveName.endsWith(".odt") -> FileType.ODT
        effectiveName.endsWith(".fodt") -> FileType.FODT
        effectiveName.extensionAfterLastDot() in codeOrDataExtensions -> FileType.HTML
        effectiveName.endsWith(".txt") -> FileType.TXT
        else -> null
    }
}

internal fun isCodeOrDataFileName(fileName: String): Boolean {
    return fileName.lowercase().withTransparentTextSuffix().extensionAfterLastDot() in codeOrDataExtensions
}

internal fun resolveFileExtensionSuffixFromName(fileName: String?): String? {
    val lowerName = fileName?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    val effectiveName = lowerName.withTransparentTextSuffix()
    val effectiveSuffix = when {
        effectiveName.endsWith(".fb2.zip") -> ".fb2.zip"
        effectiveName.endsWith(".markdown") -> ".markdown"
        effectiveName.endsWith(".xhtml") -> ".xhtml"
        effectiveName.extensionAfterLastDot() != null && resolveFileTypeFromName(effectiveName) != null -> ".${effectiveName.extensionAfterLastDot()}"
        else -> null
    } ?: return null

    return if (effectiveName != lowerName && lowerName.endsWith(".txt")) {
        "$effectiveSuffix.txt"
    } else {
        effectiveSuffix
    }
}

private fun String.withTransparentTextSuffix(): String {
    if (!endsWith(".txt")) return this
    val innerName = removeSuffix(".txt")
    if (innerName.isBlank() || !innerName.contains('.')) return this
    return if (resolveFileTypeFromNameWithoutTransparentText(innerName) != null) innerName else this
}

private fun resolveFileTypeFromNameWithoutTransparentText(fileName: String): FileType? {
    return when {
        fileName.endsWith(".cbz") -> FileType.CBZ
        fileName.endsWith(".cbr") -> FileType.CBR
        fileName.endsWith(".cb7") -> FileType.CB7
        fileName.endsWith(".pdf") -> FileType.PDF
        fileName.endsWith(".epub") -> FileType.EPUB
        fileName.endsWith(".mobi") || fileName.endsWith(".azw3") || fileName.endsWith(".prc") -> FileType.MOBI
        fileName.endsWith(".fb2") || fileName.endsWith(".fb2.zip") -> FileType.FB2
        fileName.endsWith(".md") || fileName.endsWith(".markdown") -> FileType.MD
        fileName.endsWith(".html") || fileName.endsWith(".xhtml") || fileName.endsWith(".htm") -> FileType.HTML
        fileName.endsWith(".docx") -> FileType.DOCX
        fileName.endsWith(".odt") -> FileType.ODT
        fileName.endsWith(".fodt") -> FileType.FODT
        fileName.extensionAfterLastDot() in codeOrDataExtensions -> FileType.HTML
        else -> null
    }
}

private fun String.extensionAfterLastDot(): String? {
    val dotIndex = lastIndexOf('.')
    return if (dotIndex in 0..<lastIndex) substring(dotIndex + 1) else null
}
