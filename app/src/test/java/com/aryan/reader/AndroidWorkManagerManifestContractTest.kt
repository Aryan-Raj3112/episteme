package com.aryan.reader

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

/**
 * Pins the manifest merger directives that keep WorkManager off the paths
 * where broken firmware crashes: auto-init stays removed so startup goes
 * through SafeWorkManager (#1), and the library diagnostics receiver stays
 * removed so a diagnostics broadcast can never call getInstance() directly
 * past the wrapper (triage #48).
 */
class AndroidWorkManagerManifestContractTest {
    @Test
    fun `workmanager initializer auto-init stays removed`() {
        val startupProvider = readManifest()
            .getElementsByTagName("provider")
            .asElements()
            .single { it.androidAttribute("name") == "androidx.startup.InitializationProvider" }
        val initializerMeta = startupProvider.getElementsByTagName("meta-data")
            .asElements()
            .single { it.androidAttribute("name") == "androidx.work.WorkManagerInitializer" }

        assertEquals("remove", initializerMeta.toolsAttribute("node"))
    }

    @Test
    fun `workmanager diagnostics receiver stays removed`() {
        val receiver = readManifest()
            .getElementsByTagName("receiver")
            .asElements()
            .single { it.androidAttribute("name") == "androidx.work.impl.diagnostics.DiagnosticsReceiver" }

        assertEquals("remove", receiver.toolsAttribute("node"))
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

    private fun Element.toolsAttribute(name: String): String? = attributes
        ?.getNamedItemNS("http://schemas.android.com/tools", name)
        ?.nodeValue
}
