package com.aryan.reader.pdf

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfReleaseRulesTest {

    @Test
    fun `release rules keep pdf reader and pdfium internals`() {
        val rules = readProguardRules()

        assertTrue(rules.contains("-keep class com.aryan.reader.pdf.PdfViewerScreenKt"))
        assertTrue(rules.contains("-keep class com.aryan.reader.pdf.PdfPageComposableKt"))
        assertTrue(rules.contains("-keep class io.legere.pdfiumandroid.**"))
        assertTrue(rules.contains("-keep class com.aryan.reader.pdf.NativePdfiumBridge"))
    }

    private fun readProguardRules(): String {
        val candidates = listOf(
            File("proguard-rules.pro"),
            File("app/proguard-rules.pro")
        )
        val file = candidates.firstOrNull { it.isFile }
        requireNotNull(file) { "Unable to locate app proguard-rules.pro" }
        return file.readText()
    }
}
