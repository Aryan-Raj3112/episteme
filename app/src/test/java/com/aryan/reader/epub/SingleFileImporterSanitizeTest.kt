package com.aryan.reader.epub

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `SingleFileImporter.sanitizeHtmlFragment` runs on every streamed chapter, and a chapter can be
 * millions of characters, so its intermediate copies are a real memory cost: the old version
 * serialised the parsed body to a String (`body.html()`), re-parsed that through `Jsoup.clean(...)`
 * and serialised again — three full-size copies of the chapter and two DOMs for one output — which
 * OOMed on a 256 MB heap while importing a DOCX (docs/crashlytics-triage.md#141).
 */
class SingleFileImporterSanitizeTest {

    private val importer = SingleFileImporter(mockk<Context>(relaxed = true))

    @Test
    fun `sanitising strips scripts and styles but keeps body markup and alignment`() {
        val html = """
            <p style="text-align: justify">Body text</p>
            <script>alert('leaked')</script>
            <style>p { color: red }</style>
            <noscript><img src="http://example.com/tracker.png"></noscript>
            <template><p>template body</p></template>
            <table><tbody><tr><td>cell text</td></tr></tbody></table>
        """.trimIndent()

        val cleaned = importer.sanitizeHtmlFragment(html)

        assertFalse("script text must not survive", cleaned.contains("alert(", ignoreCase = true))
        assertFalse("stylesheet must not survive", cleaned.contains("color: red", ignoreCase = true))
        assertFalse("noscript payload must not survive", cleaned.contains("tracker.png", ignoreCase = true))
        assertFalse("template payload must not survive", cleaned.contains("template body", ignoreCase = true))
        assertTrue("body text must survive", cleaned.contains("Body text"))
        assertTrue("table markup must survive", cleaned.contains("cell text"))
        // `retainTextAlignAttributes` moves text-align into `align` before the safelist drops `style`.
        assertTrue("alignment must survive", cleaned.contains("align=\"justify\""))
        assertFalse("inline style must not survive", cleaned.contains("style=", ignoreCase = true))
    }

    @Test
    fun `sanitising does not serialise and re-parse the body`() {
        val source = sourceFile("com/aryan/reader/epub/SingleFileImporter.kt").readText()
        val body = source.substringAfter("internal fun sanitizeHtmlFragment")
            .substringBefore("private fun removeLeadingDuplicateTitleText")
        // Comments explain the removed calls, so match code only.
        val code = body.lines()
            .filterNot { line ->
                val trimmed = line.trimStart()
                trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
            }
            .joinToString("\n")

        assertFalse(
            "sanitizeHtmlFragment must not serialise the parsed body (body.html())",
            code.contains("body.html()")
        )
        assertFalse(
            "sanitizeHtmlFragment must not re-parse through Jsoup.clean(String)",
            code.contains("Jsoup.clean(")
        )
        assertTrue(
            "the safelist must run on the already-parsed document",
            code.contains("Cleaner(htmlSafelist).clean(dirtyDocument)")
        )
        assertTrue(
            "the output must be serialised exactly once",
            code.indexOf("Cleaner(htmlSafelist).clean(dirtyDocument)") <
                code.indexOf(".body().html()")
        )
    }

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("app/src/main/java/$relativePath")
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Unable to locate $relativePath from ${File(".").absolutePath}")
    }
}
