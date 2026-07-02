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

    @Test
    fun `native pdfium bridge loads lazily`() {
        val source = readSourceFile("src/main/java/com/aryan/reader/pdf/NativePdfiumBridge.kt")

        assertTrue(source.contains("fun ensureLoaded(): Boolean"))
        assertTrue(!source.contains("init {\n        System.loadLibrary"))
    }

    private fun readProguardRules(): String = readSourceFile("proguard-rules.pro")

    private fun readSourceFile(path: String): String {
        val candidates = listOf(
            File(path),
            File("app", path)
        )
        val file = candidates.firstOrNull { it.isFile }
        requireNotNull(file) { "Unable to locate $path" }
        return file.readText()
    }
}
