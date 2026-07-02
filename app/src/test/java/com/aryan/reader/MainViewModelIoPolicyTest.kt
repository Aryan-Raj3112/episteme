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


    @Test
    fun `prepareBookForImport resolves provider metadata on IO dispatcher`() {
        val source = readMainViewModelSource()
        val functionBody = source.substringAfter("private suspend fun prepareBookForImport(externalUri: Uri)")
            .substringBefore("val libraryFlow")

        assertTrue(functionBody.contains("withContext(Dispatchers.IO)"))
        assertTrue(functionBody.contains("getFileNameFromUri(externalUri, appContext)"))
        assertTrue(functionBody.contains("getFileTypeFromUri(externalUri, appContext)"))
        assertTrue(functionBody.contains("FileHasher.calculateSha256"))
    }

    @Test
    fun `external intent import keeps URI provider probes off main dispatcher`() {
        val source = readMainViewModelSource()
        val importBody = source.substringAfter("private fun importExternalFile(")
            .substringBefore("private fun openTemporaryExternalFile")
        val temporaryBody = source.substringAfter("private fun openTemporaryExternalFile(externalUri: Uri)")
            .substringBefore("fun saveHighlights")

        assertTrue(importBody.contains("viewModelScope.launch(Dispatchers.IO)"))
        assertTrue(importBody.contains("getFileNameFromUri(externalUri, appContext)"))
        assertTrue(importBody.contains("getFileTypeFromUri(externalUri, appContext)"))
        assertTrue(temporaryBody.contains("viewModelScope.launch(Dispatchers.IO)"))
        assertTrue(temporaryBody.contains("getFileTypeFromUri(externalUri, appContext)"))
        assertTrue(temporaryBody.contains("getFileNameFromUri(externalUri, appContext)"))
    }

    private fun readMainViewModelSource(): String {
        return listOf(
            File("src/main/java/com/aryan/reader/MainViewModel.kt"),
            File("app/src/main/java/com/aryan/reader/MainViewModel.kt")
        ).first { it.isFile }.readText()
    }
}