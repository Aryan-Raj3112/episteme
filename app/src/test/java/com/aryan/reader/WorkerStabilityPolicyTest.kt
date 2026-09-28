package com.aryan.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards Track 4 (background work off the UI path) + model stability:
 * artwork prewarmed off Main, scan guards snapshot-based, retries bounded,
 * and library rows trusted-stable for Compose skipping.
 */
class WorkerStabilityPolicyTest {

    @Test
    fun `library models are trusted immutable`() {
        val item = readMain("data/RecentFileItem.kt")
        val tags = readMain("data/LibraryEntities.kt")
        assertTrue(item.contains("@Immutable\ndata class RecentFileItem("))
        assertTrue(tags.contains("@Immutable"))
    }

    @Test
    fun `notification artwork prewarms off main with shared cache`() {
        val source = readMain("MediaNotificationPinning.kt")
        assertTrue(source.contains("fun prewarm(context: Context, uri: Uri?)"))
        assertTrue(source.contains("prewarmExecutor"))
        assertTrue(source.contains("sharedCache"))
        // Miss path still consults the shared prewarm cache first.
        assertTrue(source.contains("synchronized(sharedCache) { sharedCache[uri.toString()] }"))
    }

    @Test
    fun `playback paths prewarm artwork before media3 asks`() {
        val audiobook = readMain("audiobook/AudiobookPlayback.kt")
        val tts = readMain("tts/TtsPlaybackManager.kt")
        assertTrue(audiobook.contains("MediaNotificationBitmapLoader.prewarm(context, artworkUri)"))
        assertTrue(tts.contains("MediaNotificationBitmapLoader.prewarm(appContext, coverImageUri?.toUri())"))
    }

    @Test
    fun `folder scan guards use run snapshot with exact write gates`() {
        val source = readMain("FolderSyncWorker.kt")
        assertTrue(source.contains("linkedUriSnapshot = folders"))
        assertTrue(source.contains("fun isFolderStillLinked(folderUriString: String, useSnapshot: Boolean = true)"))
        // Per-folder gates (pre-scan / pre-write) never serve the snapshot.
        assertTrue(source.contains("isFolderStillLinked(folder.uriString, useSnapshot = false)"))
    }

    @Test
    fun `local work retries are bounded and offline-capable`() {
        val folderSync = readMain("FolderSyncWorker.kt")
        val opds = readFile("opds/OpdsViewModel.kt")
        val main = readMain("MainViewModel.kt")
        val bookProc = readMain("paginatedreader/data/BookProcessingWorker.kt")
        for (source in listOf(folderSync, opds, main, bookProc)) {
            assertTrue(source.contains("setBackoffCriteria"))
        }
        // No network/battery constraints on offline-capable local work.
        assertFalse(folderSync.contains("setRequiredNetworkType"))
        assertFalse(folderSync.contains("setRequiresBatteryNotLow"))
    }

    @Test
    fun `workmanager state queries are time-bounded`() {
        val source = readMain("CloudFolderSyncWorker.kt")
        assertTrue(source.contains(".getWorkInfosForUniqueWork(workName).get(2, TimeUnit.SECONDS)"))
        assertFalse(source.contains("getWorkInfosForUniqueWork(workName).get()"))
    }

    private fun readMain(relativePath: String): String = readFile(relativePath)

    private fun readFile(relativePath: String): String {
        return listOf(
            File("src/main/java/com/aryan/reader/$relativePath"),
            File("app/src/main/java/com/aryan/reader/$relativePath")
        ).first { it.isFile }.readText()
    }
}
