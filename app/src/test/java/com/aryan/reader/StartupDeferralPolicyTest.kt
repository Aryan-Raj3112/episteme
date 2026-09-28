package com.aryan.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards Track 1 (cold start off Main): heavy startup work must be deferred
 * behind IO dispatchers / post-library delays, never synchronous in init.
 */
class StartupDeferralPolicyTest {

    @Test
    fun `workmanager init stays off init critical path`() {
        val source = readMainViewModelSource()
        val initBody = source.substringAfter("init {").substringBefore("private fun persistReaderSession")
        assertTrue(initBody.contains("viewModelScope.launch(Dispatchers.IO)"))
        assertTrue(initBody.contains("SafeWorkManager.cancelUniqueWork"))
        assertTrue(initBody.contains("SafeWorkManager.pruneWork"))
        // Deferred: the calls must be nested inside a launch block (12-space
        // indent), never directly in init (8-space indent).
        assertFalse(initBody.contains("\n        SafeWorkManager.cancelUniqueWork"))
        assertFalse(initBody.contains("\n        SafeWorkManager.pruneWork"))
    }

    @Test
    fun `remote config and billing init are deferred off main`() {
        val source = readMainViewModelSource()
        val initBody = source.substringAfter("init {").substringBefore("private fun persistReaderSession")
        assertTrue(initBody.contains("remoteConfigRepository.init()"))
        assertTrue(initBody.contains("billingClientWrapper.initializeConnection()"))
        assertTrue(initBody.contains("delay(2000)"))
    }

    @Test
    fun `folder sync sweep and session restore wait for first paint`() {
        val source = readMainViewModelSource()
        val initBody = source.substringAfter("init {").substringBefore("private fun persistReaderSession")
        assertTrue(initBody.contains("launchPostLibraryReady"))
        assertTrue(initBody.contains("triggerFolderSyncWorker"))
        assertTrue(initBody.contains("sweepOrphanedCache()"))
        assertTrue(initBody.contains("restoreReaderSessionIfNeeded()"))
    }

    @Test
    fun `startup gate is exposed for splash`() {
        val source = readMainViewModelSource()
        assertTrue(source.contains("val startupGatePassed"))
        assertTrue(source.contains("val isLibraryReady"))
    }

    @Test
    fun `update check is deferred off activity creation`() {
        val source = File("src/main/java/com/aryan/reader/MainActivity.kt")
            .takeIf { it.isFile }
            ?.readText()
            ?: File("app/src/main/java/com/aryan/reader/MainActivity.kt").readText()
        assertTrue(source.contains("delay(3000)"))
        assertTrue(source.contains("checkForUpdates"))
        assertTrue(source.contains("setKeepOnScreenCondition"))
    }

    private fun readMainViewModelSource(): String {
        return listOf(
            File("src/main/java/com/aryan/reader/MainViewModel.kt"),
            File("app/src/main/java/com/aryan/reader/MainViewModel.kt")
        ).first { it.isFile }.readText()
    }
}
