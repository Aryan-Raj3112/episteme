package com.aryan.reader.shared.reader

/**
 * Portable strict-XML document model shared by the EPUB package loader and the
 * generated-document parsers (FB2/ODT/DOCX). Parsing is intentionally strict:
 * invalid entities, mismatched tags, or stray root text reject the document so
 * callers can fall back instead of rendering corrupted markup.
 */
internal data class SharedXmlDocumentNode(
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
    val children: MutableList<SharedXmlDocumentNode> = mutableListOf(),
    val content: MutableList<SharedXmlDocumentContent> = mutableListOf()
) {
    val localName: String get() = name.substringAfter(':').lowercase()

    fun attribute(name: String): String? = attributes[name]

    /** Namespace-insensitive attribute lookup: matches the local part after any prefix. */
    fun attributeByLocalName(name: String): String? = attributes.entries
        .firstOrNull { it.key.substringAfterLast(':').equals(name, ignoreCase = true) }
        ?.value

    fun attributeLocalIgnoreCase(name: String): String? = attributes.entries
        .firstOrNull { it.key.substringAfter(':').equals(name, ignoreCase = true) }
        ?.value

    fun descendants(localName: String? = null): Sequence<SharedXmlDocumentNode> = sequence {
        children.forEach { child ->
            if (localName == null || child.localName == localName.lowercase()) yield(child)
            yieldAll(child.descendants(localName))
        }
    }

    fun firstDescendant(localName: String): SharedXmlDocumentNode? = descendants(localName).firstOrNull()

    fun descendantsNamed(name: String): Sequence<SharedXmlDocumentNode> = descendants().filter { it.name == name }

    fun firstDescendantNamed(primaryName: String, fallbackName: String): SharedXmlDocumentNode? =
        descendantsNamed(primaryName).firstOrNull() ?: descendantsNamed(fallbackName).firstOrNull()

    fun firstChildNamed(name: String): SharedXmlDocumentNode? = children.firstOrNull { it.name == name }

    fun androidMetadataChildren(primaryName: String, fallbackName: String): List<SharedXmlDocumentNode> =
        children.filter { it.name == primaryName }.ifEmpty { children.filter { it.name == fallbackName } }

    fun toMobileEpubMetaElement(): MobileEpubMetaElement = MobileEpubMetaElement(
        id = attribute("id")?.decodeEpubEntities(),
        name = attribute("name")?.decodeEpubEntities(),
        property = attribute("property")?.decodeEpubEntities(),
        content = attribute("content")?.decodeEpubEntities(),
        text = textContent().takeIf(String::isNotBlank)?.decodeEpubEntities(),
        refines = attribute("refines")
    )

    fun appendText(value: String) {
        if (value.isEmpty()) return
        val existing = content.lastOrNull() as? SharedXmlDocumentContent.Text
        if (existing != null) existing.value.append(value) else content += SharedXmlDocumentContent.Text(StringBuilder(value))
    }

    fun textContent(): String = buildString {
        content.forEach { part ->
            when (part) {
                is SharedXmlDocumentContent.Text -> append(part.value)
                is SharedXmlDocumentContent.Child -> append(part.node.textContent())
            }
        }
    }
}

internal sealed interface SharedXmlDocumentContent {
    data class Text(val value: StringBuilder) : SharedXmlDocumentContent
    data class Child(val node: SharedXmlDocumentNode) : SharedXmlDocumentContent
}

internal fun parseSharedXmlDocument(raw: String): SharedXmlDocumentNode? {
    val tokens = sharedEpubXmlTokens(raw).toList()
    if (tokens.any { it.value.startsWith("<!DOCTYPE", ignoreCase = true) }) {
        return null
    }
    val root = SharedXmlDocumentNode("#document")
    val stack = ArrayDeque<SharedXmlDocumentNode>().apply { addLast(root) }
    var cursor = 0
    tokens.forEach { match ->
        if (match.start > cursor) {
            val text = raw.substring(cursor, match.start)
            if (stack.size == 1 && text.isNotBlank()) return null
            if (!text.hasOnlyValidEpubXmlEntities()) return null
            stack.last().appendText(text)
        }
        val token = match.value
        when {
            token.startsWith("<!--") || token.startsWith("<?") || token.startsWith("<!DOCTYPE", true) -> Unit
            token.startsWith("<![CDATA[") -> {
                if (stack.size == 1) return null
                stack.last().appendText(token.removePrefix("<![CDATA[").removeSuffix("]]>") )
            }
            token.startsWith("</") -> {
                val closingName = token.removePrefix("</").substringBefore('>').trim()
                if (stack.size == 1 || stack.last().name != closingName) return null
                stack.removeLast()
            }
            token.startsWith("<") -> {
                val selfClosing = token.trimEnd().endsWith("/>")
                val inside = token.removePrefix("<").removeSuffix(">").removeSuffix("/").trim()
                val name = inside.takeWhile { !it.isWhitespace() }
                if (!name.matches(EpubXmlNameRegex)) return null
                val attributes = parseSharedXmlDocumentAttributes(inside.substring(name.length)) ?: return null
                val node = SharedXmlDocumentNode(name, attributes)
                stack.last().children += node
                stack.last().content += SharedXmlDocumentContent.Child(node)
                if (!selfClosing) stack.addLast(node)
            }
        }
        cursor = match.endExclusive
    }
    if (cursor < raw.length) {
        val trailing = raw.substring(cursor)
        if (stack.size == 1 && trailing.isNotBlank()) return null
        if (!trailing.hasOnlyValidEpubXmlEntities()) return null
        stack.last().appendText(trailing)
    }
    return root.children.singleOrNull().takeIf { stack.size == 1 }
}

private fun parseSharedXmlDocumentAttributes(raw: String): Map<String, String>? {
    val attributes = linkedMapOf<String, String>()
    var cursor = 0
    while (cursor < raw.length) {
        while (cursor < raw.length && raw[cursor].isWhitespace()) cursor++
        if (cursor == raw.length) break
        val match = EpubXmlAttributeRegex.find(raw, cursor)?.takeIf { it.range.first == cursor } ?: return null
        val name = match.groupValues[1]
        if (!name.matches(EpubXmlNameRegex) || attributes.containsKey(name)) return null
        val value = match.groupValues[3]
        if ('<' in value || !value.hasOnlyValidEpubXmlEntities()) return null
        attributes[name] = value.decodeEpubEntities()
        cursor = match.range.last + 1
    }
    return attributes
}

private fun String.hasOnlyValidEpubXmlEntities(): Boolean {
    var cursor = 0
    while (true) {
        val ampersand = indexOf('&', cursor)
        if (ampersand < 0) return true
        val semicolon = indexOf(';', ampersand + 1)
        if (semicolon < 0) return false
        val body = substring(ampersand + 1, semicolon)
        val valid = when {
            body in EpubXmlNamedEntities -> true
            body.startsWith("#x") || body.startsWith("#X") ->
                body.drop(2).takeIf(String::isNotEmpty)?.toIntOrNull(16)?.isValidEpubXmlCodePoint() == true
            body.startsWith('#') ->
                body.drop(1).takeIf(String::isNotEmpty)?.toIntOrNull()?.isValidEpubXmlCodePoint() == true
            else -> false
        }
        if (!valid) return false
        cursor = semicolon + 1
    }
}

private fun Int?.isValidEpubXmlCodePoint(): Boolean =
    this != null && (this == 0x9 || this == 0xA || this == 0xD || this in 0x20..0xD7FF || this in 0xE000..0xFFFD || this in 0x10000..0x10FFFF)

internal data class SharedEpubXmlToken(
    val start: Int,
    val endExclusive: Int,
    val value: String
)

internal fun sharedEpubXmlTokens(raw: String): Sequence<SharedEpubXmlToken> = sequence {
    var searchFrom = 0
    while (searchFrom < raw.length) {
        val start = raw.indexOf('<', searchFrom)
        if (start < 0) break
        val endExclusive = when {
            raw.startsWith("<!--", start) -> raw.indexOf("-->", start + 4).takeIf { it >= 0 }?.plus(3)
            raw.startsWith("<![CDATA[", start) -> raw.indexOf("]]>", start + 9).takeIf { it >= 0 }?.plus(3)
            raw.startsWith("<?", start) -> raw.indexOf("?>", start + 2).takeIf { it >= 0 }?.plus(2)
            raw.regionMatches(start, "<!DOCTYPE", 0, 9, ignoreCase = true) -> raw.sharedEpubTagEnd(start, trackDoctypeSubset = true)
            else -> raw.sharedEpubTagEnd(start, trackDoctypeSubset = false)
        } ?: break
        yield(SharedEpubXmlToken(start, endExclusive, raw.substring(start, endExclusive)))
        searchFrom = endExclusive
    }
}

private fun String.sharedEpubTagEnd(start: Int, trackDoctypeSubset: Boolean): Int? {
    var quote: Char? = null
    var subsetDepth = 0
    var index = start + 1
    while (index < length) {
        val char = this[index]
        if (quote != null) {
            if (char == quote) quote = null
        } else {
            when (char) {
                '\'', '"' -> quote = char
                '[' -> if (trackDoctypeSubset) subsetDepth++
                ']' -> if (trackDoctypeSubset && subsetDepth > 0) subsetDepth--
                '>' -> if (!trackDoctypeSubset || subsetDepth == 0) return index + 1
            }
        }
        index++
    }
    return null
}

internal val EpubXmlAttributeRegex = Regex("""(?is)([:_\p{L}][:_\p{L}\p{N}.\-\p{M}]*)\s*=\s*([\"'])(.*?)\2""")
private val EpubXmlNameRegex = Regex("""[:_\p{L}][:_\p{L}\p{N}.\-\p{M}]*""")
private val EpubXmlNamedEntities = setOf("amp", "lt", "gt", "quot", "apos")
