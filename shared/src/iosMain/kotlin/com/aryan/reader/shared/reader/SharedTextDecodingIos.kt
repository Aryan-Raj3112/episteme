package com.aryan.reader.shared.reader

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFStringConvertEncodingToNSStringEncoding
import platform.CoreFoundation.kCFStringEncodingBig5
import platform.CoreFoundation.kCFStringEncodingDOSJapanese
import platform.CoreFoundation.kCFStringEncodingDOSKorean
import platform.CoreFoundation.kCFStringEncodingDOSRussian
import platform.CoreFoundation.kCFStringEncodingEUC_JP
import platform.CoreFoundation.kCFStringEncodingEUC_KR
import platform.CoreFoundation.kCFStringEncodingGB_18030_2000
import platform.CoreFoundation.kCFStringEncodingGBK_95
import platform.CoreFoundation.kCFStringEncodingISO_2022_JP
import platform.CoreFoundation.kCFStringEncodingISOLatin1
import platform.CoreFoundation.kCFStringEncodingISOLatin2
import platform.CoreFoundation.kCFStringEncodingISOLatin9
import platform.CoreFoundation.kCFStringEncodingKOI8_R
import platform.CoreFoundation.kCFStringEncodingMacCyrillic
import platform.CoreFoundation.kCFStringEncodingShiftJIS
import platform.CoreFoundation.kCFStringEncodingWindowsArabic
import platform.CoreFoundation.kCFStringEncodingWindowsCyrillic
import platform.CoreFoundation.kCFStringEncodingWindowsGreek
import platform.CoreFoundation.kCFStringEncodingWindowsHebrew
import platform.CoreFoundation.kCFStringEncodingWindowsLatin1
import platform.CoreFoundation.kCFStringEncodingWindowsLatin2
import platform.CoreFoundation.kCFStringEncodingWindowsLatin5
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create

/**
 * Kotlin/Native ships no statistical charset detector, so legacy decoding is
 * delegated to CoreFoundation's ICU-backed converter registry, with candidate
 * selection driven by the shared [SharedCharsetPlausibility] language models.
 *
 * Pipeline for a payload that already failed strict UTF-8:
 * 1. ISO-2022 escape check ([SharedLegacyCharsetFamilies.containsIso2022Escape])
 *    — such payloads are nearly pure ASCII and must be decoded as ISO-2022-JP.
 * 2. CJK candidates ([sharedCjkCandidates]) with strict decode + script gate.
 *    These come first because CJK bytes strictly reject every single-byte
 *    codepage (verified offline), while the reverse is not true: single-byte
 *    Cyrillic/Greek "ANSI" files decode cleanly as Shift-JIS mojibake.
 * 3. Single-byte candidates ([sharedSingleByteCandidates]) with strict decode,
 *    C1-control rejection and per-script frequency scoring; the highest score
 *    wins, ties go to the earlier (more common) codepage.
 *
 * A strict decode (NSString creation) failing means the candidate is skipped —
 * malformed input never leaks through as U+FFFD.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun decodeTextWithPlatformCharset(bytes: ByteArray): String? {
    if (bytes.isEmpty()) return null
    val data = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    if (SharedLegacyCharsetFamilies.containsIso2022Escape(bytes)) {
        decodeIosStrict(data, nsStringEncoding(kCFStringEncodingISO_2022_JP))?.let { return it }
    }
    for (candidate in sharedCjkCandidates) {
        val decoded = decodeIosStrict(data, candidate.encoding) ?: continue
        val profile = SharedCharsetPlausibility.scriptProfile(decoded)
        if (SharedCharsetPlausibility.cjkAccepts(candidate.candidate, profile)) {
            return decoded
        }
    }
    var bestEncoding: ULong? = null
    var bestScore = -1f
    for (candidate in sharedSingleByteCandidates) {
        val decoded = decodeIosStrict(data, candidate.encoding) ?: continue
        if (SharedCharsetPlausibility.hasControlCharacters(decoded)) continue
        val score = SharedCharsetPlausibility.scoreSingleByteDecode(decoded, candidate.model)
        if (score > bestScore) {
            bestScore = score
            bestEncoding = candidate.encoding
        }
    }
    return bestEncoding?.let { decodeIosStrict(data, it) }
}

/**
 * Strict single-encoding decode. NSString creation fails on malformed input
 * instead of substituting replacement characters.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun decodeIosStrict(data: NSData, encoding: ULong): String? {
    val decoded = NSString.create(data = data, encoding = encoding) ?: return null
    val text = decoded.toString()
    return text.takeUnless { '\uFFFD' in it }
}

/** CJK candidates in evaluation order: kana evidence first, Han families, then Hangul. */
private val sharedCjkCandidates: List<CjkIosCandidate> = listOf(
    CjkIosCandidate(SharedCjkCandidate.SHIFT_JIS, nsStringEncoding(kCFStringEncodingShiftJIS)),
    CjkIosCandidate(SharedCjkCandidate.EUC_JP, nsStringEncoding(kCFStringEncodingEUC_JP)),
    CjkIosCandidate(SharedCjkCandidate.BIG5, nsStringEncoding(kCFStringEncodingBig5)),
    CjkIosCandidate(SharedCjkCandidate.EUC_KR, nsStringEncoding(kCFStringEncodingEUC_KR)),
    CjkIosCandidate(SharedCjkCandidate.GB18030, nsStringEncoding(kCFStringEncodingGB_18030_2000)),
    // Vendor extensions of the strict families: only reached when the strict
    // list above produced no gated decode.
    CjkIosCandidate(SharedCjkCandidate.SHIFT_JIS, nsStringEncoding(kCFStringEncodingDOSJapanese)),
    CjkIosCandidate(SharedCjkCandidate.GB18030, nsStringEncoding(kCFStringEncodingGBK_95)),
    CjkIosCandidate(SharedCjkCandidate.EUC_KR, nsStringEncoding(kCFStringEncodingDOSKorean))
)

private data class CjkIosCandidate(val candidate: SharedCjkCandidate, val encoding: ULong)

/**
 * Single-byte candidates in tie-break priority order (most common source first).
 * Each is scored against its own script model, so order only matters when two
 * candidates score equally — which happens when their decodes are identical.
 */
private val sharedSingleByteCandidates: List<SingleByteIosCandidate> = listOf(
    SingleByteIosCandidate(nsStringEncoding(kCFStringEncodingWindowsLatin1.toLong()), SharedLatinSingleByteModel),
    SingleByteIosCandidate(nsStringEncoding(kCFStringEncodingWindowsLatin5), SharedLatinSingleByteModel),
    SingleByteIosCandidate(nsStringEncoding(kCFStringEncodingWindowsLatin2), SharedLatinSingleByteModel),
    SingleByteIosCandidate(nsStringEncoding(kCFStringEncodingISOLatin9), SharedLatinSingleByteModel),
    SingleByteIosCandidate(nsStringEncoding(kCFStringEncodingISOLatin1.toLong()), SharedLatinSingleByteModel),
    SingleByteIosCandidate(nsStringEncoding(kCFStringEncodingWindowsCyrillic), SharedCyrillicSingleByteModel),
    SingleByteIosCandidate(nsStringEncoding(kCFStringEncodingKOI8_R), SharedCyrillicSingleByteModel),
    SingleByteIosCandidate(nsStringEncoding(kCFStringEncodingDOSRussian), SharedCyrillicSingleByteModel),
    SingleByteIosCandidate(nsStringEncoding(kCFStringEncodingMacCyrillic), SharedCyrillicSingleByteModel),
    SingleByteIosCandidate(nsStringEncoding(kCFStringEncodingWindowsGreek), SharedGreekSingleByteModel),
    SingleByteIosCandidate(nsStringEncoding(kCFStringEncodingWindowsHebrew), SharedHebrewSingleByteModel),
    SingleByteIosCandidate(nsStringEncoding(kCFStringEncodingWindowsArabic), SharedArabicSingleByteModel)
)

private data class SingleByteIosCandidate(val encoding: ULong, val model: SharedSingleByteModel)

private fun nsStringEncoding(cfEncoding: Long): ULong =
    CFStringConvertEncodingToNSStringEncoding(cfEncoding.toUInt())

private fun nsStringEncoding(cfEncoding: Int): ULong =
    CFStringConvertEncodingToNSStringEncoding(cfEncoding.toUInt())
