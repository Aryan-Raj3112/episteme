/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.epub

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

fun parseXMLFile(inputSteam: InputStream): Document? =
    secureDocumentBuilderFactory().newDocumentBuilder().parse(inputSteam)

fun parseXMLFile(byteArray: ByteArray): Document? {
    try {
        return parseXMLFile(byteArray.inputStream())
    } catch (e: Exception) {
        if (!isDoctypeRejection(e)) throw e
    }
    // The JDK enforces disallow-doctype-decl while Android's runtime parser
    // ignores the flag, so classic EPUB 2 files (notably toc.ncx with its
    // canonical PUBLIC doctype) parse on-device but throw in JVM unit tests.
    // Strip the declaration and retry so both runtimes behave alike. The
    // shared/iOS parser likewise skips doctypes without fetching external
    // DTDs or expanding entities, keeping the platforms at parity.
    return parseXMLFile(byteArray.withDoctypeStripped().inputStream())
}

private fun isDoctypeRejection(e: Exception): Boolean {
    var cause: Throwable? = e
    while (cause != null) {
        val message = cause.message.orEmpty()
        if (message.contains("DOCTYPE", ignoreCase = true) && message.contains("disallow", ignoreCase = true)) {
            return true
        }
        cause = cause.cause
    }
    return false
}

internal fun ByteArray.withDoctypeStripped(): ByteArray {
    // DOCTYPE scaffolding is ASCII; on non-UTF-8 encodings the marker won't
    // match and the original bytes are returned untouched.
    val text = toString(Charsets.UTF_8)
    val start = text.indexOf("<!DOCTYPE", ignoreCase = true).takeIf { it >= 0 } ?: return this
    var quote: Char? = null
    var subsetDepth = 0
    var index = start + "<!DOCTYPE".length
    while (index < text.length) {
        val char = text[index]
        if (quote != null) {
            if (char == quote) quote = null
        } else {
            when (char) {
                '\'', '"' -> quote = char
                '[' -> subsetDepth++
                ']' -> if (subsetDepth > 0) subsetDepth--
                '>' -> if (subsetDepth == 0) {
                    return (text.removeRange(start, index + 1)).toByteArray(Charsets.UTF_8)
                }
            }
        }
        index++
    }
    return this
}

fun String.asFileName(): String = this.replace("/", "_")

internal fun secureDocumentBuilderFactory(): DocumentBuilderFactory {
    return DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        setFeatureSafely(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeatureSafely("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeatureSafely("http://xml.org/sax/features/external-general-entities", false)
        setFeatureSafely("http://xml.org/sax/features/external-parameter-entities", false)
        setFeatureSafely("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        runCatching { isXIncludeAware = false }
        runCatching { isExpandEntityReferences = false }
    }
}

private fun DocumentBuilderFactory.setFeatureSafely(name: String, value: Boolean) {
    runCatching { setFeature(name, value) }
}

internal fun safeFileInRoot(root: File, childPath: String): File? {
    val rootFile = runCatching { root.canonicalFile }.getOrNull() ?: return null
    val targetFile = runCatching { File(rootFile, childPath).canonicalFile }.getOrNull() ?: return null
    return targetFile.takeIf { it.isInsideOrSame(rootFile) }
}

internal fun File.isInsideOrSame(root: File): Boolean {
    val rootPath = runCatching { root.canonicalFile.path }.getOrNull() ?: return false
    val targetPath = runCatching { canonicalFile.path }.getOrNull() ?: return false
    return targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
}

fun Document.selectFirstTag(tag: String): Node? = getElementsByTagName(tag).item(0)
fun Node.selectFirstChildTag(tag: String) = childElements.find { it.tagName == tag }
fun Node.selectChildTag(tag: String) = childElements.filter { it.tagName == tag }

/**
 * Child elements whose local name matches, ignoring any namespace prefix. The OPF
 * document is parsed namespace-unaware, so `<meta>` and `<opf:meta>` are distinct
 * tagNames and Calibre EPUB 3 packages mix both spellings in one `<metadata>` block.
 */
fun Node.selectChildTagsByLocalName(localName: String) = childElements.filter {
    it.tagName.substringAfter(':') == localName
}

fun Node.getAttributeValue(attribute: String): String? =
    attributes?.getNamedItem(attribute)?.textContent

val NodeList.elements get() = (0 until length).asSequence().mapNotNull { item(it) as? Element }
val Node.childElements get() = childNodes.elements

