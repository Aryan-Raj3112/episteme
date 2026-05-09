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
        )
    )

    private val capabilitiesByType: Map<FileType, FileTypeCapability> = all.associateBy { it.type }
    private val typesByExtension: Map<String, FileType> = all
        .flatMap { capability -> capability.extensions.map { it.lowercase() to capability.type } }
        .toMap()

    fun capabilityFor(type: FileType): FileTypeCapability? {
        return capabilitiesByType[type]
    }

    fun displayNameFor(type: FileType): String {
        return capabilityFor(type)?.displayName ?: type.name
    }

    fun fileTypeForName(fileName: String): FileType {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .substringBefore('?')
            .substringBefore('#')
            .lowercase()
        return typesByExtension[extension] ?: FileType.UNKNOWN
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
}
