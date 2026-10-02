package com.aryan.reader.shared

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "Unknown"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(units.indices)
    return formatDecimal(bytes / 1024.0.pow(digitGroups.toDouble()), 2) + " " + units[digitGroups]
}

fun progressPercentValue(progressPercentage: Float?): Int {
    return (progressPercentage ?: 0f).coerceIn(0f, 100f).roundToInt()
}

fun progressFraction(progressPercentage: Float?): Float {
    return progressPercentValue(progressPercentage) / 100f
}

fun BookItem.cardTitle(usePdfFileNameAsDisplayName: Boolean = false): String {
    if (usePdfFileNameAsDisplayName && type == FileType.PDF) return displayName
    return title?.takeIf { it.isNotBlank() } ?: displayName
}

fun BookItem.cardAuthor(fallback: String = "No author listed"): String {
    return author
        ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
        ?: fallback
}

fun BookItem.isOpdsStream(): Boolean {
    return path?.startsWith("opds-pse://") == true
}

/**
 * Mirrors Android's original-file action gate. Save/share require a concrete
 * local source and are not meaningful for streamed OPDS entries.
 */
fun BookItem.canExportOriginalFile(): Boolean {
    return path != null && !isOpdsStream()
}

/** Mirrors Android's embedded-metadata editor gate. Only a local EPUB can be rewritten. */
fun BookItem.canEditEmbeddedFileMetadata(): Boolean {
    return type == FileType.EPUB && path != null && !isOpdsStream()
}

/**
 * Whether a book belongs to the selected source folders.
 *
 * Selections are stored as Android's `uriString`, but a book's `sourceFolder`
 * is the folder *uri* on Android and the folder *name* on iOS (native scans
 * record the bookmark name). Both spellings are accepted so one stored filter
 * matches books produced by either platform.
 */
fun BookItem.matchesSourceFolders(
    sourceFolders: Set<String>,
    folderAliases: Map<String, String> = emptyMap(),
): Boolean {
    if (sourceFolders.isEmpty()) return true
    val matchesInAppStorage = IN_APP_STORAGE_SOURCE in sourceFolders &&
        sourceFolder == null &&
        !isOpdsStream()
    if (matchesInAppStorage) return true
    val folder = sourceFolder ?: return false
    if (folder in sourceFolders) return true
    // Accept the other platform's spelling of the same folder.
    return folderAliases[folder] in sourceFolders
}

private fun formatDecimal(value: Double, decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val rounded = (value * factor).roundToInt() / factor
    val text = rounded.toString()
    val dotIndex = text.indexOf('.')
    if (dotIndex < 0) return text + "." + "0".repeat(decimals)
    val currentDecimals = text.length - dotIndex - 1
    return if (currentDecimals >= decimals) {
        text.take(dotIndex + 1 + decimals)
    } else {
        text + "0".repeat(decimals - currentDecimals)
    }
}
