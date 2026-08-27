package com.aryan.reader

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudFolderDigestCountingOutputStreamTest {
    @Test
    fun bulkAndSingleByteWritesAreCountedAndHashedExactlyOnce() {
        val sink = ByteArrayOutputStream()
        val digest = MessageDigest.getInstance("SHA-256")
        val stream = CloudFolderDigestCountingOutputStream(sink, digest)
        val firstChunk = "cloud-folder".toByteArray()
        val secondChunk = "payload".toByteArray()
        val expected = firstChunk + secondChunk + byteArrayOf('!'.code.toByte())

        stream.write(firstChunk)
        stream.write(secondChunk, 0, secondChunk.size)
        stream.write('!'.code)
        stream.flush()

        assertArrayEquals(expected, sink.toByteArray())
        assertEquals(expected.size.toLong(), stream.count)
        assertArrayEquals(
            MessageDigest.getInstance("SHA-256").digest(expected),
            digest.digest(),
        )
    }
}
