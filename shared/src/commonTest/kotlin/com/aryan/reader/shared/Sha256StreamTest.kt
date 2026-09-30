package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256StreamTest {

    @Test
    fun `matches published vectors`() {
        // NIST/RFC 6234 vectors. A regression here silently changes every book
        // id, which would make the whole library re-upload to CloudKit.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(ByteArray(0)),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc".encodeToByteArray()),
        )
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256Hex(
                "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()
            ),
        )
    }

    @Test
    fun `streams a million a characters`() {
        // The classic FIPS 180-4 long vector. It is the one that actually
        // exercises a multi-block streaming hash: 1,000,000 bytes fed in 64 KiB
        // chunks, so every buffer boundary and the final padding are covered by
        // a published digest rather than a self-generated expectation.
        val expected = "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0"
        val stream = Sha256Stream()
        val chunk = ByteArray(64 * 1024) { 0x61 }
        val total = 1_000_000
        var written = 0
        while (written + chunk.size <= total) {
            stream.update(chunk)
            written += chunk.size
        }
        // The vector is for exactly 1,000,000 bytes, so the tail must be an
        // exact remainder rather than whole extra chunks.
        val remainder = total - written
        if (remainder > 0) {
            stream.update(chunk, 0, remainder)
        }
        assertEquals(total, written + remainder)
        assertEquals(
            expected,
            stream.digest().joinToString("") { byte ->
                byte.toUByte().toString(16).padStart(2, '0')
            },
        )
    }

    @Test
    fun `chunked updates equal a single update`() {
        val payload = ByteArray(200_000) { (it % 251).toByte() }
        val single = sha256Hex(payload)

        for (chunkSize in listOf(1, 63, 64, 65, 4096, 65_536)) {
            val stream = Sha256Stream()
            var offset = 0
            while (offset < payload.size) {
                val end = minOf(offset + chunkSize, payload.size)
                stream.update(payload, offset, end - offset)
                offset = end
            }
            assertEquals(
                single,
                stream.digest().joinToString("") { byte ->
                    byte.toUByte().toString(16).padStart(2, '0')
                },
                "chunkSize=$chunkSize",
            )
        }
    }
}
