package com.aryan.reader.paginatedreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MathMLRendererLifecycleTest {

    @Test
    fun `constructor does not eagerly initialize WebView`() {
        val source = readMathMLRendererSource()
        val constructorArea = source.substringAfter("class MathMLRenderer(private val context: Context)")
            .substringBefore("suspend fun awaitReady")

        assertFalse(constructorArea.contains("setupWebView()"))
        assertTrue(source.contains("ensureWebViewStarted()"))
        assertTrue(source.substringAfter("suspend fun awaitReady").substringBefore("private fun setupWebView").contains("ensureWebViewStarted()"))
    }

    private fun readMathMLRendererSource(): String {
        return listOf(
            File("src/main/java/com/aryan/reader/paginatedreader/MathMLRenderer.kt"),
            File("app/src/main/java/com/aryan/reader/paginatedreader/MathMLRenderer.kt")
        ).first { it.isFile }.readText()
    }
}