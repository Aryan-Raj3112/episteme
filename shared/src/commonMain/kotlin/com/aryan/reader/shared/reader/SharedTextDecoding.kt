package com.aryan.reader.shared.reader

/**
 * Decodes raw text-file bytes into a String regardless of source encoding.
 *
 * Legacy "ANSI" text files (for example GBK-encoded Chinese exports from Windows)
 * are not UTF-8, so naive decoding turns them into mojibake. The pipeline mirrors
 * what mainstream readers (Calibre, KOReader, VS Code auto-guess encoding) do:
 *
 * 1. BOM sniffing for UTF-8/16/32 (deterministic).
 * 2. BOM-less UTF-16 detection via the interleaved-NUL-byte pattern.
 * 3. Strict UTF-8 validation (any malformed sequence means it is not UTF-8).
 * 4. Platform legacy-charset detection ([decodeTextWithPlatformCharset]) covering
 *    GBK/GB18030, Big5, Shift-JIS, EUC-KR, windows-125x and friends.
 * 5. Per-byte Latin-1 mapping as a lossless last resort (never fails).
 */
object SharedTextDecoding {

    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        decodeBomMarkedText(bytes)?.let { return it }
        decodeBomLessUtf16(bytes)?.let { return it }
        val utf8 = bytes.decodeToString()
        if ('\uFFFD' !in utf8) return utf8.removePrefix(utf8BomChar)
        decodeTextWithPlatformCharset(bytes)?.let { return it }
        return decodeLatin1(bytes)
    }

    private fun decodeBomMarkedText(bytes: ByteArray): String? {
        if (bytes.size < Utf16BomLength) return null
        val hasUtf16LeBom = matchesAt(bytes, Utf16LeBomBytes, offset = 0)
        val hasUtf32LeBom = bytes.size >= Utf32BomLength &&
            matchesAt(bytes, Utf16LeBomBytes, offset = 0) &&
            bytes[2] == 0x00.toByte() &&
            bytes[3] == 0x00.toByte()
        if (hasUtf32LeBom) return decodeUtf32(bytes, offset = Utf32BomLength, littleEndian = true)
        if (matchesAt(bytes, Utf32BeBomBytes, offset = 0)) {
            return decodeUtf32(bytes, offset = Utf32BomLength, littleEndian = false)
        }
        if (hasUtf16LeBom) return decodeUtf16(bytes, offset = Utf16BomLength, littleEndian = true)
        if (matchesAt(bytes, Utf16BeBomBytes, offset = 0)) {
            return decodeUtf16(bytes, offset = Utf16BomLength, littleEndian = false)
        }
        return null
    }

    /**
     * BOM-less UTF-16 shows up as NUL bytes concentrated on one side of every
     * 16-bit unit (the high byte of Latin-range text). Real prose never
     * contains NUL bytes in any legacy encoding, while UTF-16LE/BE payloads do
     * — including mixed-script text where only most, not all, units qualify.
     */
    private fun decodeBomLessUtf16(bytes: ByteArray): String? {
        if (bytes.size < MinimumBomLessUtf16Bytes || bytes.size % 2 != 0) return null
        var evenZeros = 0
        var oddZeros = 0
        for (index in bytes.indices) {
            if (bytes[index] != 0.toByte()) continue
            if (index % 2 == 0) evenZeros++ else oddZeros++
        }
        val nulDominantSide = maxOf(evenZeros, oddZeros)
        return when {
            nulDominantSide < bytes.size / Utf16NulDensityDivisor -> null
            oddZeros >= evenZeros -> decodeUtf16(bytes, offset = 0, littleEndian = true)
            else -> decodeUtf16(bytes, offset = 0, littleEndian = false)
        }?.takeIf { '\u0000' !in it }
    }

    private fun decodeUtf16(bytes: ByteArray, offset: Int, littleEndian: Boolean): String {
        val unitCount = (bytes.size - offset) / 2
        return buildString(unitCount) {
            var index = offset
            while (index + 1 < bytes.size) {
                val unit = if (littleEndian) {
                    (bytes[index].toInt() and 0xFF) or ((bytes[index + 1].toInt() and 0xFF) shl 8)
                } else {
                    ((bytes[index].toInt() and 0xFF) shl 8) or (bytes[index + 1].toInt() and 0xFF)
                }
                append(unit.toChar())
                index += 2
            }
        }
    }

    private fun decodeUtf32(bytes: ByteArray, offset: Int, littleEndian: Boolean): String {
        return buildString((bytes.size - offset) / Utf32UnitLength) {
            var index = offset
            while (index + Utf32UnitLength <= bytes.size) {
                var value = 0
                if (littleEndian) {
                    for (shift in Utf32UnitLength - 1 downTo 0) {
                        value = (value shl 8) or (bytes[index + shift].toInt() and 0xFF)
                    }
                } else {
                    for (shift in 0 until Utf32UnitLength) {
                        value = (value shl 8) or (bytes[index + shift].toInt() and 0xFF)
                    }
                }
                if (value in 0..MaxUnicodeScalar) {
                    if (value > MaxBmpScalar) {
                        val adjusted = value - SurrogateOffset
                        append(((adjusted ushr 10) + HighSurrogateBase).toChar())
                        append(((adjusted and 0x3FF) + LowSurrogateBase).toChar())
                    } else {
                        append(value.toChar())
                    }
                }
                index += Utf32UnitLength
            }
        }
    }

    private fun decodeLatin1(bytes: ByteArray): String {
        return buildString(bytes.size) {
            for (byte in bytes) append(byte.toInt() and 0xFF)
        }
    }

    private fun matchesAt(bytes: ByteArray, pattern: ByteArray, offset: Int): Boolean {
        if (bytes.size < offset + pattern.size) return false
        for ((index, expected) in pattern.withIndex()) {
            if (bytes[offset + index] != expected) return false
        }
        return true
    }

    private const val utf8BomChar = "\uFEFF"
    private val Utf16BeBomBytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
    private val Utf16LeBomBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val Utf32BeBomBytes = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0xFE.toByte(), 0xFF.toByte())
    private const val Utf16BomLength = 2
    private const val Utf32BomLength = 4
    private const val Utf32UnitLength = 4
    private const val MinimumBomLessUtf16Bytes = 16
    private const val Utf16NulDensityDivisor = 4
    private const val MaxUnicodeScalar = 0x10FFFF
    private const val MaxBmpScalar = 0xFFFF
    private const val SurrogateOffset = 0x10000
    private const val HighSurrogateBase = 0xD800
    private const val LowSurrogateBase = 0xDC00
}

/**
 * Platform hook: best-effort decode of legacy (non-UTF) encoded bytes, or null
 * when the platform cannot confidently identify a charset. Runs only after
 * strict UTF-8 validation has failed.
 */
internal expect fun decodeTextWithPlatformCharset(bytes: ByteArray): String?
