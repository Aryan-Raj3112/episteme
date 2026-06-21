package com.aryan.reader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainViewModelIoPolicyTest {

    @Test
    fun `saveOriginalPdf copies bytes on IO dispatcher`() {
        val source = readMainViewModelSource()
        val functionBody = source.substringAfter("fun saveOriginalPdf(sourceUri: Uri, destUri: Uri)")
            .substringBefore("internal fun trackExternalOpenForClose")

        assertTrue(functionBody.contains("withContext(Dispatchers.IO)"))
        assertTrue(functionBody.contains("copyUriBytes(sourceUri, destUri)"))
    }

    private fun readMainViewModelSource(): String {
        return listOf(
            File("src/main/java/com/aryan/reader/MainViewModel.kt"),
            File("app/src/main/java/com/aryan/reader/MainViewModel.kt")
        ).first { it.isFile }.readText()
    }
}