package com.aryan.reader.shared.ui

import com.aryan.reader.shared.sharedHtmlToPlainText
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedXmlDocumentNode
import com.aryan.reader.shared.reader.parseSharedXmlDocument

/**
 * A note resolved from an EPUB `noteref`/footnote link.
 *
 * The shared mobile reader intentionally exposes plain text here.  The source
 * XHTML is already sanitized while the book is loaded, but a platform HTML
 * renderer is not available to common Compose code.  Keeping the resolver's
 * result structured means Android and iOS can add richer rendering later
 * without changing link classification or package lookup.
 */
internal data class SharedMobileEpubFootnote(
    val plainText: String,
    val targetChapterIndex: Int,
    val targetHref: String,
    val fragment: String,
)

/**
 * Resolves an EPUB footnote without navigating the reader.
 *
 * A link is treated as a note when its source anchor is marked with
 * `epub:type="noteref"`/`role="doc-noteref"`, or when the target itself is a
 * semantic footnote/endnote.  Ordinary links to ordinary anchors return null
 * and continue through the normal internal-link path.
 */
internal fun SharedEpubBook.resolveMobileEpubFootnote(
    rawHref: String,
    ownerHref: String? = null,
    sourceChapterIndex: Int? = null,
): SharedMobileEpubFootnote? {
    val href = rawHref.trim()
    if (href.isBlank() || href.isExternalEpubLink() || href.hasUnsupportedEpubScheme()) return null

    val sourceIndex = sourceChapterIndex?.takeIf { it in chapters.indices }
        ?: ownerHref?.let { owner ->
            chapters.indexOfFirst { chapter ->
                chapter.baseHref?.normalizeMobileEpubPath() == owner.normalizeMobileEpubPath()
            }.takeIf { it >= 0 }
        }
        ?: return null
    val sourceChapter = chapters.getOrNull(sourceIndex) ?: return null
    val sourceOwner = ownerHref?.takeIf(String::isNotBlank) ?: sourceChapter.baseHref.orEmpty()

    val fragment = href.substringAfter('#', missingDelimiterValue = "")
        .substringBefore('?')
        .percentDecodeMobileEpubPath()
        .takeIf(String::isNotBlank)
        ?: return null
    val reference = href.substringBefore('#').substringBefore('?').percentDecodeMobileEpubPath()
    val targetPath = if (reference.isBlank()) {
        sourceOwner.normalizeMobileEpubPath()
    } else {
        resolveMobileEpubPath(sourceOwner, reference)
    }
    if (targetPath.isBlank()) return null

    val targetIndex = chapters.indexOfFirst { chapter ->
        chapter.baseHref?.normalizeMobileEpubPath() == targetPath
    }.takeIf { it >= 0 } ?: return null
    val targetChapter = chapters[targetIndex]

    val sourceDocument = sourceChapter.htmlContent.takeIf(String::isNotBlank)
        ?.let(::parseSharedXmlDocument)
    val sourceNoteref = sourceDocument?.containsNoterefAnchor(href, sourceOwner) == true ||
        sourceChapter.htmlContent.containsNoterefAnchor(href, sourceOwner)

    val targetDocument = targetChapter.htmlContent.takeIf(String::isNotBlank)
        ?.let(::parseSharedXmlDocument)
        ?: return null
    val targetPathNodes = targetDocument.findNodePath { node ->
        node.attributeByLocalName("id")?.trim() == fragment
    } ?: return null
    val targetNode = targetPathNodes.last()
    val semanticContainer = targetPathNodes.lastOrNull(SharedXmlDocumentNode::isSemanticNote)
    if (!sourceNoteref && semanticContainer == null) return null

    val contentNode = semanticContainer ?: targetPathNodes
        .dropLast(1)
        .lastOrNull { it.localName in EpubFootnoteFallbackContainers }
        ?: targetNode
    val plainText = sharedHtmlToPlainText(contentNode.textContent())
        .takeIf(String::isNotBlank)
        ?: return null

    return SharedMobileEpubFootnote(
        plainText = plainText,
        targetChapterIndex = targetIndex,
        targetHref = targetChapter.baseHref ?: targetPath,
        fragment = fragment,
    )
}

private val EpubFootnoteFallbackContainers = setOf("aside", "li", "p", "div", "section")

private fun SharedXmlDocumentNode.isSemanticNote(): Boolean {
    return attributeByLocalName("type").hasSemanticEpubToken("footnote", "endnote") ||
        attributeByLocalName("role").hasSemanticEpubToken("doc-footnote", "doc-endnote") ||
        attributeByLocalName("class")
            .orEmpty()
            .split(Regex("\\s+"))
            .any { it.equals("footnote", true) || it.equals("endnote", true) }
}

private fun String?.hasSemanticEpubToken(vararg expected: String): Boolean {
    return this.orEmpty().split(Regex("\\s+")).any { token ->
        expected.any { it.equals(token, ignoreCase = true) }
    }
}

private fun SharedXmlDocumentNode.containsNoterefAnchor(
    rawHref: String,
    ownerHref: String,
): Boolean {
    return descendants()
        .filter { it.localName == "a" }
        .any { anchor ->
            val href = anchor.attributeByLocalName("href") ?: return@any false
            if (!footnoteHrefEquivalent(href, rawHref, ownerHref)) return@any false
            anchor.attributeByLocalName("type").hasSemanticEpubToken("noteref") ||
                anchor.attributeByLocalName("role").hasSemanticEpubToken("doc-noteref")
        }
}

/** Lenient fallback for XHTML fragments that are not strict XML documents. */
private fun String.containsNoterefAnchor(rawHref: String, ownerHref: String): Boolean {
    val anchorRegex = Regex("(?is)<a\\b[^>]*>")
    return anchorRegex.findAll(this).any { match ->
        val attributes = match.value.parseFootnoteAttributes()
        val href = attributes["href"] ?: return@any false
        if (!footnoteHrefEquivalent(href, rawHref, ownerHref)) return@any false
        attributes["type"].hasSemanticEpubToken("noteref") ||
            attributes["role"].hasSemanticEpubToken("doc-noteref")
    }
}

private fun String.parseFootnoteAttributes(): Map<String, String> {
    return Regex("(?is)([A-Za-z_:][A-Za-z0-9_.:-]*)\\s*=\\s*([\\\"'])(.*?)\\2")
        .findAll(this)
        .associate { match ->
            match.groupValues[1].substringAfterLast(':').lowercase() to match.groupValues[3]
        }
}

private fun String.hasUnsupportedEpubScheme(): Boolean {
    val reference = substringBefore('#').substringBefore('?').trim()
    val colon = reference.indexOf(':')
    if (colon <= 0) return false
    return reference.substring(0, colon).any { !(it.isLetterOrDigit() || it == '+' || it == '-' || it == '.') }
        .not()
}

private fun footnoteHrefEquivalent(actual: String, expected: String, ownerHref: String): Boolean {
    val normalize = { value: String ->
        val path = value.substringBefore('#').substringBefore('?').percentDecodeMobileEpubPath()
        val fragment = value.substringAfter('#', missingDelimiterValue = "")
            .substringBefore('?')
            .percentDecodeMobileEpubPath()
        val resolved = if (path.isBlank()) ownerHref else resolveMobileEpubPath(ownerHref, path)
        "$resolved#$fragment"
    }
    return normalize(actual) == normalize(expected)
}

private fun SharedXmlDocumentNode.findNodePath(
    predicate: (SharedXmlDocumentNode) -> Boolean,
): List<SharedXmlDocumentNode>? {
    fun search(node: SharedXmlDocumentNode, path: List<SharedXmlDocumentNode>): List<SharedXmlDocumentNode>? {
        val currentPath = path + node
        if (predicate(node)) return currentPath
        node.children.forEach { child ->
            search(child, currentPath)?.let { return it }
        }
        return null
    }
    return search(this, emptyList())
}
