package com.aryan.reader.shared

enum class ReaderPlatform {
    ANDROID,
    DESKTOP
}

enum class ReaderFeatureSurface {
    PDF_VIEWER,
    EPUB_READER,
    TEXT_READER
}

data class FileTypeCapability(
    val type: FileType,
    val displayName: String,
    val extensions: Set<String>,
    val androidSurface: ReaderFeatureSurface?,
    val desktopSurface: ReaderFeatureSurface?,
    val syncEligible: Boolean = true
) {
    val isReadableOnAndroid: Boolean get() = androidSurface != null
    val isReadableOnDesktop: Boolean get() = desktopSurface != null

    fun surfaceFor(platform: ReaderPlatform): ReaderFeatureSurface? {
        return when (platform) {
            ReaderPlatform.ANDROID -> androidSurface
            ReaderPlatform.DESKTOP -> desktopSurface
        }
    }
}

object SharedFileCapabilities {
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

    private val manualOnlyReaderMimeTypes = setOf(
        "text/csv",
        "text/comma-separated-values",
        "text/tab-separated-values",
        "application/json",
        "application/xml",
        "text/xml",
        "text/x-java-source",
        "text/x-python",
        "text/x-kotlin",
        "text/javascript",
        "application/javascript",
        "text/x-c",
        "text/x-c++",
        "text/x-csharp",
        "text/x-ruby",
        "text/x-go",
        "text/x-log"
    )

    val androidFilePickerMimeTypes: List<String> = listOf(
        "application/pdf",
        "application/epub+zip",
        "application/x-mobipocket-ebook",
        "application/vnd.amazon.ebook",
        "application/vnd.amazon.mobi8-ebook",
        "text/markdown",
        "text/x-markdown",
        "text/plain",
        "text/html",
        "application/xhtml+xml",
        "application/x-fictionbook+xml",
        "application/x-zip-compressed-fb2",
        "application/zip",
        "application/vnd.comicbook+zip",
        "application/x-cbz",
        "application/vnd.comicbook-rar",
        "application/x-cbr",
        "application/x-rar-compressed",
        "application/x-cb7",
        "application/x-7z-compressed",
        "application/vnd.comicbook+tar",
        "application/x-cbt",
        "application/x-tar",
        "application/tar",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.oasis.opendocument.text",
        "application/x-vnd.oasis.opendocument.text-flat-xml"
    ) + manualOnlyReaderMimeTypes

    val all: List<FileTypeCapability> = listOf(
        FileTypeCapability(
            type = FileType.EPUB,
            displayName = "EPUB",
            extensions = setOf("epub"),
            androidSurface = ReaderFeatureSurface.EPUB_READER,
            desktopSurface = ReaderFeatureSurface.EPUB_READER
        ),
        FileTypeCapability(
            type = FileType.PDF,
            displayName = "PDF",
            extensions = setOf("pdf"),
            androidSurface = ReaderFeatureSurface.PDF_VIEWER,
            desktopSurface = ReaderFeatureSurface.PDF_VIEWER
        ),
        FileTypeCapability(
            type = FileType.TXT,
            displayName = "TXT",
            extensions = setOf("txt"),
            androidSurface = ReaderFeatureSurface.EPUB_READER,
            desktopSurface = ReaderFeatureSurface.TEXT_READER
        ),
        FileTypeCapability(
            type = FileType.MD,
            displayName = "Markdown",
            extensions = setOf("md", "markdown"),
            androidSurface = ReaderFeatureSurface.EPUB_READER,
            desktopSurface = ReaderFeatureSurface.TEXT_READER
        ),
        FileTypeCapability(
            type = FileType.HTML,
            displayName = "HTML",
            extensions = setOf("html", "htm", "xhtml"),
            androidSurface = ReaderFeatureSurface.EPUB_READER,
            desktopSurface = ReaderFeatureSurface.TEXT_READER
        ),
        FileTypeCapability(
            type = FileType.MOBI,
            displayName = "MOBI",
            extensions = setOf("mobi", "azw", "azw3", "prc"),
            androidSurface = ReaderFeatureSurface.EPUB_READER,
            desktopSurface = ReaderFeatureSurface.TEXT_READER
        ),
        FileTypeCapability(
            type = FileType.FB2,
            displayName = "FB2",
            extensions = setOf("fb2"),
            androidSurface = ReaderFeatureSurface.EPUB_READER,
            desktopSurface = ReaderFeatureSurface.TEXT_READER
        ),
        FileTypeCapability(
            type = FileType.CBZ,
            displayName = "CBZ",
            extensions = setOf("cbz"),
            androidSurface = ReaderFeatureSurface.PDF_VIEWER,
            desktopSurface = ReaderFeatureSurface.PDF_VIEWER
        ),
        FileTypeCapability(
            type = FileType.CBR,
            displayName = "CBR",
            extensions = setOf("cbr"),
            androidSurface = ReaderFeatureSurface.PDF_VIEWER,
            desktopSurface = ReaderFeatureSurface.PDF_VIEWER
        ),
        FileTypeCapability(
            type = FileType.CB7,
            displayName = "CB7",
            extensions = setOf("cb7"),
            androidSurface = ReaderFeatureSurface.PDF_VIEWER,
            desktopSurface = ReaderFeatureSurface.PDF_VIEWER
        ),
        FileTypeCapability(
            type = FileType.CBT,
            displayName = "CBT",
            extensions = setOf("cbt"),
            androidSurface = ReaderFeatureSurface.PDF_VIEWER,
            desktopSurface = ReaderFeatureSurface.PDF_VIEWER
        ),
        FileTypeCapability(
            type = FileType.DOCX,
            displayName = "DOCX",
            extensions = setOf("docx"),
            androidSurface = ReaderFeatureSurface.EPUB_READER,
            desktopSurface = ReaderFeatureSurface.TEXT_READER
        ),
        FileTypeCapability(
            type = FileType.ODT,
            displayName = "ODT",
            extensions = setOf("odt"),
            androidSurface = ReaderFeatureSurface.EPUB_READER,
            desktopSurface = ReaderFeatureSurface.TEXT_READER
        ),
        FileTypeCapability(
            type = FileType.FODT,
            displayName = "FODT",
            extensions = setOf("fodt"),
            androidSurface = ReaderFeatureSurface.EPUB_READER,
            desktopSurface = ReaderFeatureSurface.TEXT_READER
        ),
        FileTypeCapability(
            type = FileType.PPTX,
            displayName = "PPTX",
            extensions = setOf("pptx"),
            androidSurface = ReaderFeatureSurface.PDF_VIEWER,
            desktopSurface = ReaderFeatureSurface.PDF_VIEWER
        )
    )

    private val capabilitiesByType: Map<FileType, FileTypeCapability> = all.associateBy { it.type }
    private val typesByExtension: Map<String, FileType> = all
        .flatMap { capability -> capability.extensions.map { it.lowercase() to capability.type } }
        .toMap()
    private val mimeTypesByType: Map<FileType, String> = mapOf(
        FileType.PDF to "application/pdf",
        FileType.EPUB to "application/epub+zip",
        FileType.MOBI to "application/x-mobipocket-ebook",
        FileType.MD to "text/markdown",
        FileType.TXT to "text/plain",
        FileType.HTML to "text/html",
        FileType.FB2 to "application/x-fictionbook+xml",
        FileType.CBZ to "application/zip",
        FileType.CBR to "application/zip",
        FileType.CB7 to "application/zip",
        FileType.CBT to "application/x-tar",
        FileType.DOCX to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        FileType.PPTX to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        FileType.ODT to "application/vnd.oasis.opendocument.text",
        FileType.FODT to "application/x-vnd.oasis.opendocument.text-flat-xml"
    )
    val comicArchiveTypes: Set<FileType> = setOf(FileType.CBZ, FileType.CBR, FileType.CB7, FileType.CBT)
    val knownFileTypes: Set<FileType> = all.mapTo(mutableSetOf()) { it.type }

    fun capabilityFor(type: FileType): FileTypeCapability? {
        return capabilitiesByType[type]
    }

    fun displayNameFor(type: FileType): String {
        return capabilityFor(type)?.displayName ?: type.name
    }

    fun primaryExtensionFor(type: FileType): String? {
        return capabilityFor(type)?.extensions?.firstOrNull()
    }

    fun mimeTypeFor(type: FileType): String? {
        return mimeTypesByType[type]
    }

    fun isComicArchive(type: FileType): Boolean {
        return type in comicArchiveTypes
    }

    fun fileTypeForName(fileName: String): FileType {
        return resolveFileTypeForName(fileName) ?: FileType.UNKNOWN
    }

    fun resolveFileTypeForName(fileName: String?): FileType? {
        val normalized = fileName?.normalizedFileName()?.takeIf { it.isNotBlank() } ?: return null
        val effectiveName = normalized.withTransparentTextSuffix()
        return fileTypeForEffectiveName(effectiveName)
    }

    fun resolveFileTypeForMetadata(fileName: String?, mimeType: String?): FileType? {
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
            "application/vnd.comicbook+tar", "application/x-cbt", "application/x-tar", "application/tar" -> {
                if (fileName?.endsWith(".cbt", ignoreCase = true) == true) FileType.CBT else null
            }
            "application/pdf" -> FileType.PDF
            "application/epub+zip" -> FileType.EPUB
            "application/x-fictionbook+xml", "application/x-zip-compressed-fb2" -> FileType.FB2
            "application/x-mobipocket-ebook",
            "application/vnd.amazon.ebook",
            "application/vnd.amazon.mobi8-ebook" -> FileType.MOBI
            "text/markdown", "text/x-markdown" -> FileType.MD
            "text/html", "application/xhtml+xml" -> FileType.HTML
            "text/plain" -> resolveFileTypeForName(fileName) ?: FileType.TXT
            else -> {
                if (normalizedMimeType != null && normalizedMimeType in manualOnlyReaderMimeTypes) {
                    FileType.HTML
                } else {
                    resolveFileTypeForName(fileName)
                }
            }
        }
    }

    fun isCodeOrDataFileName(fileName: String): Boolean {
        return fileName.normalizedFileName()
            .withTransparentTextSuffix()
            .extensionAfterLastDot() in codeOrDataExtensions
    }

    fun isManualOnlyReaderFileName(fileName: String?): Boolean {
        return fileName?.let(::isCodeOrDataFileName) ?: false
    }

    fun isManualOnlyReaderMimeType(mimeType: String?): Boolean {
        val normalized = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?: return false
        return normalized in manualOnlyReaderMimeTypes
    }

    fun isLocalFolderSyncEligibleFile(name: String, mimeType: String?): Boolean {
        if (isManualOnlyReaderFileName(name)) return false
        if (resolveFileTypeForName(name) != null) return true
        return !isManualOnlyReaderMimeType(mimeType)
    }

    fun fileExtensionSuffixForName(fileName: String?): String? {
        val normalized = fileName?.normalizedFileName()?.takeIf { it.isNotBlank() } ?: return null
        val effectiveName = normalized.withTransparentTextSuffix()
        val effectiveSuffix = when {
            effectiveName.endsWith(".fb2.zip") -> ".fb2.zip"
            effectiveName.endsWith(".markdown") -> ".markdown"
            effectiveName.endsWith(".xhtml") -> ".xhtml"
            effectiveName.extensionAfterLastDot() != null && resolveFileTypeForName(effectiveName) != null -> {
                ".${effectiveName.extensionAfterLastDot()}"
            }
            else -> null
        } ?: return null

        return if (effectiveName != normalized && normalized.endsWith(".txt")) {
            "$effectiveSuffix.txt"
        } else {
            effectiveSuffix
        }
    }

    fun surfaceFor(type: FileType, platform: ReaderPlatform): ReaderFeatureSurface? {
        return capabilityFor(type)?.surfaceFor(platform)
    }

    fun canOpen(type: FileType, platform: ReaderPlatform): Boolean {
        return surfaceFor(type, platform) != null
    }

    fun readableTypesFor(platform: ReaderPlatform): Set<FileType> {
        return all.mapNotNullTo(mutableSetOf()) { capability ->
            capability.type.takeIf { capability.surfaceFor(platform) != null }
        }
    }

    fun readableTypesFor(platform: ReaderPlatform, surface: ReaderFeatureSurface): Set<FileType> {
        return all.mapNotNullTo(mutableSetOf()) { capability ->
            capability.type.takeIf { capability.surfaceFor(platform) == surface }
        }
    }

    fun syncableTypesFor(platform: ReaderPlatform): Set<FileType> {
        return all.mapNotNullTo(mutableSetOf()) { capability ->
            capability.type.takeIf { capability.syncEligible && capability.surfaceFor(platform) != null }
        }
    }

    fun supportedFormatsLabel(platform: ReaderPlatform): String {
        return all
            .filter { it.surfaceFor(platform) != null }
            .joinToString(", ") { it.displayName }
    }

    fun desktopParityGaps(): List<FileType> {
        return all
            .filter { it.isReadableOnAndroid && !it.isReadableOnDesktop }
            .map { it.type }
    }

    private fun fileTypeForEffectiveName(fileName: String): FileType? {
        if (fileName.endsWith(".fb2.zip")) return FileType.FB2
        val extension = fileName.extensionAfterLastDot() ?: return null
        if (extension in codeOrDataExtensions) return FileType.HTML
        return typesByExtension[extension]
    }

    private fun String.normalizedFileName(): String {
        return trim()
            .substringBefore('?')
            .substringBefore('#')
            .lowercase()
    }

    private fun String.withTransparentTextSuffix(): String {
        if (!endsWith(".txt")) return this
        val innerName = removeSuffix(".txt")
        if (innerName.isBlank() || !innerName.contains('.')) return this
        return if (fileTypeForEffectiveName(innerName) != null) innerName else this
    }

    private fun String.extensionAfterLastDot(): String? {
        val dotIndex = lastIndexOf('.')
        return if (dotIndex > 0 && dotIndex < lastIndex) substring(dotIndex + 1) else null
    }
}
