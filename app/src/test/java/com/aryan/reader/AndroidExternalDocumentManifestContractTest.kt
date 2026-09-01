package com.aryan.reader

import com.aryan.reader.shared.SharedFileCapabilities
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class AndroidExternalDocumentManifestContractTest {
    @Test
    fun `router advertises every shared external document mime for all handoff actions`() {
        val router = readManifest()
            .getElementsByTagName("activity")
            .asElements()
            .single { it.androidAttribute("name") == ".ExternalFileOpenRouterActivity" }
        val filters = router.getElementsByTagName("intent-filter").asElements()
        val sendActions = setOf(
            "android.intent.action.SEND",
            "android.intent.action.SEND_MULTIPLE",
        )
        val advertisedMimeTypes = filters
            .flatMap { filter ->
                filter.getElementsByTagName("data").asElements().mapNotNull {
                    it.androidAttribute("mimeType")
                }
            }
            .toSet()

        assertTrue(
            "Manifest is missing shared MIME types: " +
                (SharedFileCapabilities.androidExternalDocumentMimeTypes - advertisedMimeTypes),
            advertisedMimeTypes.containsAll(SharedFileCapabilities.androidExternalDocumentMimeTypes),
        )
        assertTrue("Wildcard MIME types are too broad", "*/*" !in advertisedMimeTypes)

        val sendFilters = filters.filter { filter ->
            filter.getElementsByTagName("action").asElements()
                .mapNotNull { it.androidAttribute("name") }
                .toSet()
                .containsAll(sendActions)
        }
        assertTrue("No dedicated SEND/SEND_MULTIPLE filter", sendFilters.isNotEmpty())
        val sendMimeTypes = sendFilters.flatMap { filter ->
            filter.getElementsByTagName("data").asElements().mapNotNull {
                it.androidAttribute("mimeType")
            }
        }.toSet()
        assertTrue(
            "SEND filter is missing shared MIME types: " +
                (SharedFileCapabilities.androidExternalDocumentMimeTypes - sendMimeTypes),
            sendMimeTypes.containsAll(SharedFileCapabilities.androidExternalDocumentMimeTypes),
        )
        assertTrue(
            "SEND filters must not require a URI scheme",
            sendFilters.any { filter ->
                filter.getElementsByTagName("data").asElements()
                    .none { it.androidAttribute("scheme") != null }
            },
        )
        assertTrue(
            "SEND filters must not be BROWSABLE",
            sendFilters.any { filter ->
                filter.getElementsByTagName("category").asElements()
                    .none { it.androidAttribute("name") == "android.intent.category.BROWSABLE" }
            },
        )
    }

    private fun readManifest(): org.w3c.dom.Document {
        val manifest = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first { it.isFile }
        return DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifest)
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> = buildList {
        for (index in 0 until length) {
            (item(index) as? Element)?.let(::add)
        }
    }

    private fun Element.androidAttribute(name: String): String? = attributes
        ?.getNamedItemNS("http://schemas.android.com/apk/res/android", name)
        ?.nodeValue
}
