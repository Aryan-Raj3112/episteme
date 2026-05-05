package com.aryan.reader

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class FileHasherTest {

    @Test
    fun `calculateSha256 returns known SHA-256 for stream content`() = runTest {
        val hash = FileHasher.calculateSha256 {
            ByteArrayInputStream("hello world".toByteArray())
        }

        assertEquals(
            "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
            hash
        )
    }

    @Test
    fun `calculateSha256 supports large multi-buffer streams`() = runTest {
        val bytes = ByteArray(20_000) { index -> (index % 127).toByte() }

        val first = FileHasher.calculateSha256 { ByteArrayInputStream(bytes) }
        val second = FileHasher.calculateSha256 {
            object : InputStream() {
                private var index = 0
                override fun read(): Int {
                    if (index >= bytes.size) return -1
                    return bytes[index++].toInt() and 0xff
                }
            }
        }

        assertEquals(first, second)
    }

    @Test
    fun `calculateSha256 returns null when provider is null or stream throws`() = runTest {
        assertNull(FileHasher.calculateSha256 { null })
        assertNull(
            FileHasher.calculateSha256 {
                object : InputStream() {
                    override fun read(): Int = throw IOException("boom")
                }
            }
        )
    }
}
