package com.aryan.reader

import android.content.ContentResolver
import android.content.ContextWrapper
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.io.InputStream

class BookImporterTransferTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun failedSourceCopyLeavesNoPartialBookOrStagingFile() = runBlocking {
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        val sourceUri = mockk<Uri>(relaxed = true)
        every { sourceUri.path } returns "/books/failing.epub"
        every { sourceUri.lastPathSegment } returns "failing.epub"
        val context = ImporterTestContext(temporaryFolder.root, contentResolver)
        every { contentResolver.openInputStream(sourceUri) } returns FailingInputStream()

        val imported = BookImporter(context).importBook(sourceUri)
        val booksDirectory = temporaryFolder.root.resolve("books")

        assertNull(imported)
        assertTrue(booksDirectory.isDirectory)
        assertFalse(booksDirectory.listFiles().orEmpty().any())
    }
}

private class ImporterTestContext(
    private val filesDirectory: java.io.File,
    private val resolver: ContentResolver,
) : ContextWrapper(mockk(relaxed = true)) {
    override fun getFilesDir(): java.io.File = filesDirectory

    override fun getContentResolver(): ContentResolver = resolver
}

private class FailingInputStream : InputStream() {
    private var hasReturnedPartialByte = false

    override fun read(): Int {
        if (!hasReturnedPartialByte) {
            hasReturnedPartialByte = true
            return 'p'.code
        }
        throw IOException("simulated source read failure")
    }
}
