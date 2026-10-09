package com.aryan.reader.shared.reader

/**
 * EPUB image source identity: name, extension, MIME type, and download file name.
 *
 * Parity item B9. Android's `EpubReaderImageReference` and the shared
 * `ReaderImageReference` each carried their own copy of this logic, and the two
 * copies disagreed about which of their two source fields to trust:
 *
 *  - `sourceName()` preferred `originalSource`,
 *  - `suggestedDownloadFileName()` preferred `sourcePath`'s extension,
 *  - `mimeType()` read `sourcePath` first, then `originalSource`,
 *  - `readDownloadBytes()` used `sourcePath` only.
 *
 * So the same image could be named after one source, typed from another, and
 * loaded from a third. This resolves the pair **once**, in one order, for both.
 *
 * The order is [resolved] first, then [declared]. `resolved` is the source the
 * host has already turned into something loadable — an absolute file path on
 * Android, a resolved EPUB-relative path in shared. It is the better answer for
 * both naming and typing because it is percent-decoded and normalized, and it is
 * the only field that is guaranteed non-blank. [declared] is the author's
 * original `src`, kept purely as a fallback.
 */
class ReaderImageSourceIdentity(
    /** The host's resolved, loadable source. Wins when non-blank. */
    resolved: String?,
    /** The author's original `src`, used only when [resolved] is blank. */
    declared: String? = null,
) {
    /** The single source every derivation below reads from. */
    val effective: String = resolved?.trim()?.takeIf { it.isNotBlank() }
        ?: declared?.trim().orEmpty()

    val isInlineDataUri: Boolean get() = effective.startsWith("data:", ignoreCase = true)

    /**
     * File name shown to the user, or null for inline `data:` sources which have
     * no meaningful name.
     */
    fun sourceName(): String? {
        if (isInlineDataUri) return null
        return effective
            .substringBefore('#')
            .substringBefore('?')
            .replace('\\', '/')
            .substringAfterLast('/')
            .takeIf { it.isNotBlank() }
    }

    /** Lowercase image extension, or null when it cannot be determined. */
    fun extension(): String? {
        // A shared EPUB resource URL has no extension of its own — the real entry name lives
        // after the book id — so unwrap it before falling back to the literal tail.
        parseSharedEpubResourceUrl(effective)?.let { reference ->
            reference.entryPath.readerImageExtensionFromPath()?.let { return it }
        }
        return effective.readerImageExtension()
    }

    /** MIME type for the source, defaulting to the `image/&#42;` wildcard. */
    fun mimeType(): String {
        readerDataUriImageMimeType(effective)?.let { return it }
        return when (extension()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "avif" -> "image/avif"
            else -> "image/*"
        }
    }

    /**
     * Suggested download file name, built from [altText] when present, else the
     * source name without its extension, else a positional fallback.
     */
    fun suggestedDownloadFileName(altText: String?, index: Int): String {
        val fallbackBase = "image-${index + 1}"
        val base = altText?.trim()?.takeIf { it.isNotBlank() }
            ?: sourceName()?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: fallbackBase
        val safeBase = base.sanitizedReaderImageFileBase().ifBlank { fallbackBase }
        val safeExtension = extension()?.takeIf { it.isNotBlank() } ?: "png"
        return "$safeBase.$safeExtension"
    }
}

/** MIME type declared by an inline `data:` image source, or null. */
fun readerDataUriImageMimeType(source: String): String? {
    if (!source.startsWith("data:", ignoreCase = true)) return null
    return source
        .drop(5)
        .substringBefore(';')
        .substringBefore(',')
        .trim()
        .takeIf { it.startsWith("image/", ignoreCase = true) }
}

private val READER_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "avif")

/**
 * Lowercase extension for an image source, or null. Reads the inline `data:`
 * MIME first so a data URI without a file extension still types correctly.
 */
fun String.readerImageExtension(): String? {
    readerDataUriImageMimeType(this)?.let { mime ->
        return mime.readerDataUriImageExtension()
    }
    return readerImageExtensionFromPath()
}

/** Lowercase extension taken from a path's literal tail, or null if unrecognized. */
fun String.readerImageExtensionFromPath(): String? {
    val extension = substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('.', "")
        .lowercase()
    return extension.takeIf { it in READER_IMAGE_EXTENSIONS }
}

private fun String.readerDataUriImageExtension(): String? = when (lowercase()) {
    "image/jpeg" -> "jpg"
    "image/png" -> "png"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "image/bmp" -> "bmp"
    "image/svg+xml" -> "svg"
    "image/avif" -> "avif"
    else -> null
}

/** File-name base with characters that are illegal on common filesystems replaced. */
fun String.sanitizedReaderImageFileBase(): String {
    return replace(Regex("""[\\/:*?"<>|]+"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trim('.')
        .take(80)
}
