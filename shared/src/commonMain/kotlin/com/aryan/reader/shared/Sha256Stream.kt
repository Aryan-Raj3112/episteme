package com.aryan.reader.shared

/**
 * Incremental SHA-256.
 *
 * Lives in `commonMain` (pure Kotlin, no platform types) so the correctness
 * vectors can be tested by the shared test suite rather than only on device.
 * The one-shot `sha256` in LocalFolderSync.ios.kt delegates here so there is
 * exactly one implementation.
 *
 * Android derives a book's cloud identity from a content hash
 * (`FileHasher.calculateSha256`); iOS needs the same so a book imported on one
 * device and downloaded on another resolves to one record instead of two.
 */
internal class Sha256Stream {
    private val block = ByteArray(64)
    private var blockLength = 0
    private var totalBytes = 0L

    private var h0 = 0x6a09e667
    private var h1 = -0x4498517b
    private var h2 = 0x3c6ef372
    private var h3 = -0x5ab00ac6
    private var h4 = 0x510e527f
    private var h5 = -0x64fa9774
    private var h6 = 0x1f83d9ab
    private var h7 = 0x5be0cd19

    private val words = IntArray(64)

    fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        var index = offset
        val end = offset + length
        totalBytes += length
        while (index < end) {
            val take = minOf(64 - blockLength, end - index)
            bytes.copyInto(block, blockLength, index, index + take)
            blockLength += take
            index += take
            if (blockLength == 64) {
                compress(block, 0)
                blockLength = 0
            }
        }
    }

    fun update(bytes: ByteArray) = update(bytes, 0, bytes.size)

    /** Pad and finalize. Must not be called twice. */
    fun digest(): ByteArray {
        val messageLengthBits = totalBytes * 8L
        // 0x80, then zeros so the length lands in the last 8 bytes of a block.
        val tail = ByteArray(if (blockLength < 56) 64 - blockLength else 128 - blockLength)
        tail[0] = 0x80.toByte()
        for (index in 0 until 8) {
            tail[tail.lastIndex - index] = (messageLengthBits ushr (index * 8)).toByte()
        }
        var index = 0
        while (index < tail.size) {
            val take = minOf(64 - blockLength, tail.size - index)
            tail.copyInto(block, blockLength, index, index + take)
            blockLength += take
            index += take
            if (blockLength == 64) {
                compress(block, 0)
                blockLength = 0
            }
        }
        val output = ByteArray(32)
        intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7).forEachIndexed { slot, value ->
            val base = slot * 4
            output[base] = (value ushr 24).toByte()
            output[base + 1] = (value ushr 16).toByte()
            output[base + 2] = (value ushr 8).toByte()
            output[base + 3] = value.toByte()
        }
        return output
    }

    private fun compress(source: ByteArray, offset: Int) {
        for (index in 0 until 16) {
            val base = offset + index * 4
            words[index] =
                ((source[base].toInt() and 0xff) shl 24) or
                    ((source[base + 1].toInt() and 0xff) shl 16) or
                    ((source[base + 2].toInt() and 0xff) shl 8) or
                    (source[base + 3].toInt() and 0xff)
        }
        for (index in 16 until 64) {
            val w15 = words[index - 15]
            val w2 = words[index - 2]
            val s0 = w15.rotateRight(7) xor w15.rotateRight(18) xor (w15 ushr 3)
            val s1 = w2.rotateRight(17) xor w2.rotateRight(19) xor (w2 ushr 10)
            words[index] = words[index - 16] + s0 + words[index - 7] + s1
        }

        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        var f = h5
        var g = h6
        var h = h7

        for (index in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + s1 + ch + SHA256_K[index] + words[index]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj
            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
        h5 += f
        h6 += g
        h7 += h
    }
}

/** FIPS 180-4 round constants (copied verbatim from the pre-existing implementation). */
internal val SHA256_K = intArrayOf(
    0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
    -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
    -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
    -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e
)

/** One-shot SHA-256. Equivalent to feeding the whole array in one `update`. */
internal fun sha256Hex(input: ByteArray): String =
    Sha256Stream().apply { update(input) }.digest().joinToString("") { byte ->
        byte.toUByte().toString(16).padStart(2, '0')
    }
