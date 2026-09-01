package com.aryan.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AndroidBackupRulesContractTest {
    @Test
    fun `android 12 rules use encrypted cloud allowlist and device transfer originals`() {
        val document = parse("xml/data_extraction_rules.xml")
        val root = document.documentElement
        val cloud = root.child("cloud-backup")
        val device = root.child("device-transfer")

        assertEquals("true", cloud.getAttribute("disableIfNoEncryptionCapabilities"))
        assertPolicy(cloud, includeOriginals = false)
        assertPolicy(device, includeOriginals = true)

        val allText = read("xml/data_extraction_rules.xml")
        assertFalse(allText.contains("rich_doc_"))
        assertFalse(allText.contains("_reflow.html"))
        assertFalse(
            root.elements("include").any { it.getAttribute("path") == "." },
        )
    }

    @Test
    fun `legacy rules keep api 26 common policy and gate originals on api 28`() {
        val base = parse("xml/backup_rules.xml").documentElement
        val api28 = parse("xml-v28/backup_rules.xml").documentElement

        assertPolicy(base, includeOriginals = false)
        assertPolicy(api28, includeOriginals = true)
        api28.elements("include")
            .filter { it.getAttribute("domain") == "file" && it.getAttribute("path") in ORIGINALS }
            .forEach { assertEquals("deviceToDeviceTransfer", it.getAttribute("requireFlags")) }
    }

    private fun assertPolicy(root: Element, includeOriginals: Boolean) {
        val includes = root.elements("include").map { it.getAttribute("domain") to it.getAttribute("path") }.toSet()

        REQUIRED_INCLUDES.forEach { assertTrue("missing include $it", it in includes) }
        // The policy is an allowlist: only included paths are backed up, so
        // <exclude> entries would be dead config that lint-vital rejects.
        assertTrue(
            "allowlist policy must not contain exclude entries",
            root.elements("exclude").isEmpty()
        )
        NEVER_BACKED_UP.forEach { path ->
            assertFalse(
                "sensitive or generated path $path must never be included",
                ("file" to path) in includes ||
                    ("sharedpref" to path) in includes ||
                    ("database" to path) in includes
            )
        }
        ORIGINALS.forEach { path ->
            val entry = "file" to path
            assertEquals("original include mismatch for $path", includeOriginals, entry in includes)
        }
    }

    private fun parse(relativePath: String) = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(readFile(relativePath))

    private fun read(relativePath: String): String = readFile(relativePath).readText()

    private fun readFile(relativePath: String): File {
        return sequenceOf(
            File("src/main/res/$relativePath"),
            File("app/src/main/res/$relativePath"),
        ).firstOrNull(File::isFile)
            ?: error("Unable to locate $relativePath")
    }

    private fun Element.child(name: String): Element = elements(name).single()

    private fun Element.elements(name: String): List<Element> {
        val nodes = getElementsByTagName(name)
        return buildList(nodes.length) {
            for (index in 0 until nodes.length) add(nodes.item(index) as Element)
        }
    }

    private companion object {
        val REQUIRED_INCLUDES = setOf(
            "sharedpref" to "reader_prefs.xml",
            "sharedpref" to "epub_reader_settings.xml",
            "sharedpref" to "epub_reader_bookmarks.xml",
            "sharedpref" to "reader_slider_chrome_prefs.xml",
            "sharedpref" to "listen_sleep_timer_preferences.xml",
            "sharedpref" to "annotation_settings_global.xml",
            "database" to "reader_database",
            "file" to "annotations",
            "file" to "page_layouts",
            "file" to "textboxes",
            "file" to "pdf_highlights",
            "file" to "custom_fonts",
            "file" to "reader_textures",
        )

        /** Paths that must stay out of every backup section. */
        val NEVER_BACKED_UP = setOf(
            "ai_byok_prefs.xml",
            "reader_opds_prefs.xml",
            "reader_user_prefs.xml",
            "book_cache_database",
            "pdf_text_cache_db",
            "androidx.work.workdb",
            "cover_cache",
            "TTS_Cache",
            "derived",
        )

        val ORIGINALS = setOf("books", "audiobooks", "metadata_backups")
    }
}
