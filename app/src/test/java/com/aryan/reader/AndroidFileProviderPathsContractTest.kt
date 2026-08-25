package com.aryan.reader

import com.aryan.reader.shared.AndroidShareArtifactManager
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class AndroidFileProviderPathsContractTest {
    @Test
    fun `provider exposes only the bounded share root`() {
        val paths = readPaths()
            .getElementsByTagName("cache-path")
            .asElements()

        assertEquals(1, paths.size)
        assertEquals(AndroidShareArtifactManager.SHARE_ROOT_DIRECTORY, paths.single().attribute("name"))
        assertEquals("${AndroidShareArtifactManager.SHARE_ROOT_DIRECTORY}/", paths.single().attribute("path"))
        assertFalse(paths.any { it.attribute("path") == "." })
        assertTrue(paths.all { it.attribute("path")?.contains("..") != true })
    }

    private fun readPaths(): org.w3c.dom.Document {
        val resource = listOf(
            File("src/main/res/xml/provider_paths.xml"),
            File("app/src/main/res/xml/provider_paths.xml"),
        ).first { it.isFile }
        return DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(resource)
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> = buildList {
        for (index in 0 until length) {
            (item(index) as? Element)?.let(::add)
        }
    }

    private fun Element.attribute(name: String): String? = attributes?.getNamedItem(name)?.nodeValue
}
