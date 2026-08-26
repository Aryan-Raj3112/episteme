package com.aryan.reader.shared.ui

import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.isReaderExternalHref
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubTocEntry
import com.aryan.reader.shared.reader.findElementOffset

internal fun String.isExternalEpubLink(): Boolean {
    return isReaderExternalHref(this)
}

internal fun String.containsReaderFragment(fragment: String): Boolean {
    val escaped = Regex.escape(fragment)
    return Regex("""\bid\s*=\s*([\"'])$escaped\1""", RegexOption.IGNORE_CASE).containsMatchIn(this)
}

internal fun resolveMobileEpubPath(owner: String, reference: String): String {
    if (reference.startsWith('/')) return reference.removePrefix("/").normalizeMobileEpubPath()
    val base = owner.substringBeforeLast('/', missingDelimiterValue = "")
    return (if (base.isBlank()) reference else "$base/$reference").normalizeMobileEpubPath()
}

internal fun String.percentDecodeMobileEpubPath(): String {
    val bytes = ArrayList<Byte>(length)
    var index = 0
    while (index < length) {
        if (this[index] == '%' && index + 2 < length) {
            val decoded = substring(index + 1, index + 3).toIntOrNull(16)
            if (decoded != null) {
                bytes += decoded.toByte()
                index += 3
                continue
            }
        }
        bytes += this[index].toString().encodeToByteArray().toList()
        index++
    }
    return bytes.toByteArray().decodeToString()
}

internal fun String.normalizeMobileEpubPath(): String {
    val parts = ArrayDeque<String>()
    replace('\\', '/').split('/').forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> if (parts.isNotEmpty()) parts.removeLast()
            else -> parts.addLast(part)
        }
    }
    return parts.joinToString("/")
}

/**
 * Resolves a TOC entry to the exact page holding its fragment anchor, mirroring
 * [com.aryan.reader.shared.reader.ReaderEngine.resolveLink] on desktop. Falls back
 * to the chapter's first page when no fragment or offset is found.
 */
internal fun SharedEpubBook.locatorForTocEntry(entry: SharedEpubTocEntry, pages: List<ReaderPage>): ReaderLocator? {
    val entryPath = entry.href
        .substringBefore('#')
        .substringBefore('?')
        .percentDecodeMobileEpubPath()
    val chapterIndex = chapters.indexOfFirst {
        it.baseHref?.normalizeMobileEpubPath() == entryPath.normalizeMobileEpubPath() &&
            it.fragmentId == entry.fragmentId
    }.takeIf { it >= 0 } ?: chapters.indexOfFirst {
        it.baseHref?.normalizeMobileEpubPath() == entryPath.normalizeMobileEpubPath()
    }
        .takeIf { it >= 0 } ?: return null
    val chapter = chapters[chapterIndex]
    val fragment = entry.fragmentId
        ?.percentDecodeMobileEpubPath()
        ?.takeIf(String::isNotBlank)
    return buildEpubAnchorLocator(chapterIndex, fragment, pages)
}

/**
 * Resolves an internal EPUB link (with optional fragment anchor) to the exact
 * page holding the anchor, mirroring
 * [com.aryan.reader.shared.reader.ReaderEngine.resolveLink] on desktop. Falls back
 * to the chapter's first page when no fragment or offset is found.
 */
internal fun SharedEpubBook.locatorForLink(
    rawHref: String,
    ownerHref: String?,
    pages: List<ReaderPage>
): Pair<ReaderLocator, String?>? {
    val fragment = rawHref.substringAfter('#', missingDelimiterValue = "")
        .substringBefore('?')
        .percentDecodeMobileEpubPath()
        .takeIf(String::isNotBlank)
    val reference = rawHref.substringBefore('#').substringBefore('?').percentDecodeMobileEpubPath()
    val targetPath = if (reference.isBlank()) ownerHref.orEmpty() else resolveMobileEpubPath(ownerHref.orEmpty(), reference)
    val chapterIndex = chapters.indexOfFirst {
        it.baseHref?.normalizeMobileEpubPath() == targetPath.normalizeMobileEpubPath() &&
            it.fragmentId == fragment
    }.takeIf { it >= 0 } ?: chapters.indexOfFirst {
        it.baseHref?.normalizeMobileEpubPath() == targetPath.normalizeMobileEpubPath()
    }
        .takeIf { it >= 0 } ?: return null
    return buildEpubAnchorLocator(chapterIndex, fragment, pages)?.let { locator -> locator to fragment }
}

private fun SharedEpubBook.buildEpubAnchorLocator(
    chapterIndex: Int,
    fragment: String?,
    pages: List<ReaderPage>
): ReaderLocator? {
    val chapter = chapters.getOrNull(chapterIndex) ?: return null
    val targetOffset = fragment?.let { chapter.semanticBlocks.findElementOffset(it) }
    val page = targetOffset?.let { offset ->
        pages.firstOrNull { it.chapterIndex == chapterIndex && offset in it.startOffset..it.endOffset }
    } ?: pages.firstOrNull { it.chapterIndex == chapterIndex }
    return ReaderLocator(
        chapterIndex = chapterIndex,
        chapterId = chapter.id,
        href = chapter.baseHref,
        pageIndex = page?.pageIndex,
        startOffset = targetOffset ?: page?.startOffset ?: 0,
        endOffset = targetOffset ?: page?.startOffset ?: 0,
        textQuote = page?.text?.take(120)
    )
}
