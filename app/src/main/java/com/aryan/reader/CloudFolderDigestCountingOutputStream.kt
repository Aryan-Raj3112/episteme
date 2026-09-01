package com.aryan.reader

import java.io.OutputStream
import java.security.MessageDigest

/**
 * Copies bytes to [output] while calculating an exact payload digest.
 *
 * This intentionally delegates directly to the wrapped stream. A
 * FilterOutputStream implementation is allowed to implement its bulk-write
 * method in terms of write(Int); overriding both methods on top of that can
 * therefore count and hash every bulk buffer twice.
 */
internal class CloudFolderDigestCountingOutputStream(
    private val output: OutputStream,
    private val digest: MessageDigest,
) : OutputStream() {
    var count: Long = 0L
        private set

    override fun write(value: Int) {
        output.write(value)
        digest.update(value.toByte())
        count++
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        output.write(buffer, offset, length)
        digest.update(buffer, offset, length)
        count += length
    }

    override fun flush() {
        output.flush()
    }

    override fun close() {
        output.close()
    }
}
