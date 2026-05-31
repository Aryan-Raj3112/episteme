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

fun parseXMLFile(byteArray: ByteArray): Document? = parseXMLFile(byteArray.inputStream())

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
fun Node.getAttributeValue(attribute: String): String? =
    attributes?.getNamedItem(attribute)?.textContent

val NodeList.elements get() = (0 until length).asSequence().mapNotNull { item(it) as? Element }
val Node.childElements get() = childNodes.elements

