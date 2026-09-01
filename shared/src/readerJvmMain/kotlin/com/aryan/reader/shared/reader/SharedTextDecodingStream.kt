package com.aryan.reader.shared.reader

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.SequenceInputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Streams text through the decision tree of [SharedTextDecoding.decode] without
 * materializing the whole source in memory: the encoding is decided from a bounded
 * leading sample and the payload is then decoded incrementally from the stream.
 *
 * Decisions are byte-identical with [SharedTextDecoding.decode] for sources that fit
 * inside [StreamingDetectionSampleBytes]. Beyond that, BOM-less UTF-16 NUL-density and
 * strict UTF-8 validation are evaluated on the sample only, mirroring the sample-based
 * legacy-charset detection (juniversalchardet already inspects just 64KB).
 *
 * Closing the returned reader closes [source].
 */
fun openLenientDecodedReader(source: InputStream): BufferedReader {
    val sample = source.readUpTo(StreamingDetectionSampleBytes)

    fun buffered(
        charset: Charset,
        headSkipBytes: Int = 0
    ): BufferedReader = BufferedReader(
        InputStreamReader(
            SequenceInputStream(ByteArrayInputStream(sample, headSkipBytes, sample.size - headSkipBytes), source),
            charset
        )
    )

    when (val bom = SharedTextDecoding.detectSharedTextBom(sample)) {
        SharedTextDecoding.SharedTextBomKind.Utf32Le ->
            return BufferedReader(Utf32UnitReader(sample, source, littleEndian = true, headSkipBytes = 4))
        SharedTextDecoding.SharedTextBomKind.Utf32Be ->
            return BufferedReader(Utf32UnitReader(sample, source, littleEndian = false, headSkipBytes = 4))
        SharedTextDecoding.SharedTextBomKind.Utf16Le ->
            return buffered(StandardCharsets.UTF_16LE, headSkipBytes = 2)
        SharedTextDecoding.SharedTextBomKind.Utf16Be ->
            return buffered(StandardCharsets.UTF_16BE, headSkipBytes = 2)
        SharedTextDecoding.SharedTextBomKind.Utf8 ->
            return buffered(StandardCharsets.UTF_8, headSkipBytes = 3)
        null -> Unit
    }

    // BOM-less UTF-16: decide on the even-length truncated sample, then apply
    // decode()'s NUL guard on the sample before committing the whole stream.
    val evenSampleLength = sample.size - (sample.size % 2)
    val bomLessUtf16LittleEndian = SharedTextDecoding.detectBomLessUtf16Endianness(sample, evenSampleLength)
    if (bomLessUtf16LittleEndian != null) {
        val sampleUnits = SharedTextDecoding.decodeUtf16(sample, offset = 0, littleEndian = bomLessUtf16LittleEndian)
        if ('\u0000' !in sampleUnits) {
            return buffered(if (bomLessUtf16LittleEndian) StandardCharsets.UTF_16LE else StandardCharsets.UTF_16BE)
        }
    }

    // Strict UTF-8 validation. A sample cut mid-sequence must not read as malformed,
    // so the trailing incomplete sequence is excluded from the check only.
    val utf8Boundary = utf8ValidatedPrefixEnd(sample)
    if ('\uFFFD' !in sample.decodeToString(0, utf8Boundary)) {
        return buffered(StandardCharsets.UTF_8)
    }

    // Legacy charset detection over the same bounded sample decode() uses (64KB).
    detectPlatformCharsetName(sample)?.let { detectedName ->
        if (!detectedName.equals("UTF-8", ignoreCase = true) && !detectedName.equals("US-ASCII", ignoreCase = true)) {
            val charset = runCatching { Charset.forName(detectedName) }.getOrNull()
            if (charset != null) return buffered(charset)
        }
    }

    // Lossless per-byte Latin-1 mapping as the last resort.
    return buffered(StandardCharsets.ISO_8859_1)
}

private const val StreamingDetectionSampleBytes = 1024 * 1024

/** Reads at most [maxBytes] bytes, blocking until the buffer fills or the stream ends. */
private fun InputStream.readUpTo(maxBytes: Int): ByteArray {
    val buffer = ByteArray(maxBytes)
    var filled = 0
    while (filled < maxBytes) {
        val read = read(buffer, filled, maxBytes - filled)
        if (read < 0) break
        filled += read
    }
    return if (filled == maxBytes) buffer else buffer.copyOf(filled)
}

/**
 * Returns the end index of the sample prefix that is safe to validate as UTF-8.
 * A trailing truncated multi-byte sequence (possible only when the sample was cut
 * mid-file) is excluded; malformed bytes elsewhere are left in so validation fails.
 */
private fun utf8ValidatedPrefixEnd(sample: ByteArray): Int {
    if (sample.isEmpty()) return 0
    val scanFloor = maxOf(0, sample.size - MaxUtf8SequenceBytes)
    for (index in sample.size - 1 downTo scanFloor) {
        val byte = sample[index].toInt() and 0xFF
        if (byte and 0x80 == 0) return sample.size
        if (byte and 0xC0 == 0xC0) {
            val sequenceLength = when {
                byte and 0xE0 == 0xC0 -> 2
                byte and 0xF0 == 0xE0 -> 3
                byte and 0xF8 == 0xF0 -> 4
                else -> return sample.size
            }
            return if (sample.size - index >= sequenceLength) sample.size else index
        }
    }
    return sample.size
}

private const val MaxUtf8SequenceBytes = 4

/**
 * Streaming UTF-32 decoder mirroring [SharedTextDecoding]'s manual unit decode
 * (the JVM/Android charset registry has no UTF-32). Values outside the Unicode
 * scalar range are dropped and a partial trailing unit at EOF is discarded.
 */
private class Utf32UnitReader(
    sample: ByteArray,
    private val source: InputStream,
    private val littleEndian: Boolean,
    headSkipBytes: Int
) : Reader() {

    private val stream = SequenceInputStream(
        ByteArrayInputStream(sample, headSkipBytes, sample.size - headSkipBytes),
        source
    )
    private val unit = ByteArray(4)
    private val pending = CharArray(2)
    private var pendingCount = 0
    private var pendingOffset = 0

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var written = 0
        while (written < len) {
            while (pendingCount > 0) {
                cbuf[off + written] = pending[pendingOffset]
                pendingOffset++
                pendingCount--
                written++
                if (written == len) return written
            }
            val value = readUnit() ?: return if (written == 0) -1 else written
            when {
                // Out-of-range scalars are dropped, matching the whole-file decoder.
                value !in 0..MaxUnicodeScalar -> Unit
                value > MaxBmpScalar -> {
                    val adjusted = value - SurrogateOffset
                    pending[0] = ((adjusted ushr 10) + HighSurrogateBase).toChar()
                    pending[1] = ((adjusted and 0x3FF) + LowSurrogateBase).toChar()
                    pendingOffset = 0
                    pendingCount = 2
                }
                else -> {
                    cbuf[off + written] = value.toChar()
                    written++
                }
            }
        }
        return written
    }

    override fun close() {
        stream.close()
    }

    private fun readUnit(): Int? {
        var filled = 0
        while (filled < 4) {
            val read = stream.read(unit, filled, 4 - filled)
            if (read < 0) return null
            filled += read
        }
        var value = 0
        if (littleEndian) {
            for (shift in 3 downTo 0) {
                value = (value shl 8) or (unit[shift].toInt() and 0xFF)
            }
        } else {
            for (shift in 0 until 4) {
                value = (value shl 8) or (unit[shift].toInt() and 0xFF)
            }
        }
        return value
    }

    private companion object {
        private const val MaxUnicodeScalar = 0x10FFFF
        private const val MaxBmpScalar = 0xFFFF
        private const val SurrogateOffset = 0x10000
        private const val HighSurrogateBase = 0xD800
        private const val LowSurrogateBase = 0xDC00
    }
}
