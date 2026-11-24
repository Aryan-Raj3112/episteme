package com.aryan.reader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the bounded book-upload fan-out (#194). A library-wide sync queues one upload per
 * book; unbounded, every in-flight upload keeps a resumable-upload buffer alive and
 * `uploadingBookIds` (an immutable set copied on every change) grows to library size, which is
 * how a 256 MB heap ran out while copying that set.
 */
class MainViewModelCloudUploadPolicyTest {

    @Test
    fun `content upload entry point runs inside the bounded gate`() {
        val source = readMainViewModelSource()
        val entryPoint = source.substringAfter("private fun uploadNewBookAndMetadata(book: RecentFileItem)")
            .substringBefore("private suspend fun uploadBookContent(book: RecentFileItem)")

        assertTrue(entryPoint.contains("bookContentUploadGate.withPermit {"))
        assertTrue(entryPoint.contains("uploadBookContent(book)"))
        assertTrue(
            "the uploading-state update has to stay inside the gate, or the set grows with the library",
            entryPoint.indexOf("bookContentUploadGate.withPermit {") <
                entryPoint.indexOf("uploadingBookIds = it.uploadingBookIds + book.bookId"),
        )
    }

    @Test
    fun `metadata upload content guard also runs inside the gate`() {
        val source = readMainViewModelSource()
        val contentGuard = source.substringAfter("android.upload.content_guard_start")
            .substringBefore("android.upload.content_guard_failed")

        assertTrue(contentGuard.contains("bookContentUploadGate.withPermit {"))
        assertTrue(contentGuard.contains("googleDriveRepository.uploadFile("))
    }

    @Test
    fun `gate keeps concurrent uploads small`() {
        val source = readMainViewModelSource()
        val permits = source.substringAfter("MAX_CONCURRENT_BOOK_CONTENT_UPLOADS =")
            .substringBefore("\n").trim().toInt()

        assertTrue("upload fan-out must stay small on a 256 MB heap", permits in 1..3)
        assertTrue(source.contains("Semaphore(MAX_CONCURRENT_BOOK_CONTENT_UPLOADS)"))
    }

    private fun readMainViewModelSource(): String {
        return listOf(
            File("src/main/java/com/aryan/reader/MainViewModel.kt"),
            File("app/src/main/java/com/aryan/reader/MainViewModel.kt")
        ).first { it.isFile }.readText()
    }
}