package com.aryan.reader.shared.reader

/**
 * Coarse byte-level classification of a non-UTF payload, used by platforms
 * without a statistical charset detector to pick which legacy-charset
 * candidate families to try and in which order.
 *
 * ISO-2022 text (JP/KR/CN) is almost entirely 7-bit ASCII with rare 0x1B
 * escape introducers — the escape byte is the only reliable marker and must
 * be checked before any encoding is attempted. All other legacy families are
 * disambiguated by the strict-decode + plausibility-model pipeline
 * ([SharedCharsetPlausibility]), so a coarse family enum is not needed.
 */
object SharedLegacyCharsetFamilies {

    /** True when the payload's leading bytes contain an ISO-2022 escape introducer (ESC). */
    fun containsIso2022Escape(bytes: ByteArray): Boolean {
        val limit = minOf(bytes.size, Iso2022ScanLimit)
        for (index in 0 until limit) {
            if (bytes[index].toInt() == 0x1B) return true
        }
        return false
    }

    private const val Iso2022ScanLimit = 512
}
