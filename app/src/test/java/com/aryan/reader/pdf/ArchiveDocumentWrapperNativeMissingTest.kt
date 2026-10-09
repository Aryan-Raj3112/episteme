package com.aryan.reader.pdf

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Guards the broken-install crash where `libarchive-jni.so` is missing from the
 * ABI split (crashlytics-triage #57).
 *
 * `Archive`'s static initializer throws `UnsatisfiedLinkError`, and every later
 * reference throws `NoClassDefFoundError`. Both are `LinkageError`s, so the
 * `catch (e: Exception)` around archive extraction does not contain them and the
 * error escapes the `ArchiveDocumentWrapper` constructor — which crashed
 * add-to-recent and background thumbnail generation outright.
 *
 * JVM unit tests have no native libraries at all, so the failure reproduces
 * here naturally: this is the real broken-install path, not a mock.
 */
@RunWith(RobolectricTestRunner::class)
class ArchiveDocumentWrapperNativeMissingTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @After
    fun tearDown() {
        ArchiveSupport.resetForTests()
    }

    /**
     * Writes a file that is *not* a readable ZIP, forcing the constructor down
     * the libarchive path (where the native load is attempted).
     */
    private fun nonZipArchive(): File =
        temporaryFolder.newFile("comic.cbr").apply { writeText("not a zip archive") }

    @Test
    fun `constructor survives missing native library instead of throwing`() {
        val error = runCatching { ArchiveDocumentWrapper(nonZipArchive()) }.exceptionOrNull()

        assertEquals(
            "ArchiveDocumentWrapper must not propagate a LinkageError from the native load",
            null,
            error
        )
    }

    @Test
    fun `degrades to zero pages when native library is unavailable`() = runBlocking {
        val wrapper = ArchiveDocumentWrapper(nonZipArchive())

        assertEquals(
            "a comic that cannot be decoded must report no pages, not crash",
            0,
            wrapper.getPageCount()
        )
        assertEquals(null, wrapper.openPage(0))
        wrapper.close()
    }

    @Test
    fun `unavailability is latched and reported only once`() {
        assertTrue(!ArchiveSupport.isUnavailable)

        val first = ArchiveSupport.reportUnavailable(UnsatisfiedLinkError("libarchive-jni.so"))
        val second = ArchiveSupport.reportUnavailable(NoClassDefFoundError("me.zhanghai.android.libarchive.Archive"))

        assertTrue(ArchiveSupport.isUnavailable)
        assertEquals(
            "the broken-install condition is permanent, so it must be reported once",
            1,
            listOf(first, second).count { it }
        )
    }
}