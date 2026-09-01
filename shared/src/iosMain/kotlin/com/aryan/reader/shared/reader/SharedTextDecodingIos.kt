package com.aryan.reader.shared.reader

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFStringConvertEncodingToNSStringEncoding
import platform.CoreFoundation.kCFStringEncodingBig5_HKSCS_1999
import platform.CoreFoundation.kCFStringEncodingDOSJapanese
import platform.CoreFoundation.kCFStringEncodingDOSKorean
import platform.CoreFoundation.kCFStringEncodingGB_18030_2000
import platform.CoreFoundation.kCFStringEncodingGBK_95
import platform.CoreFoundation.kCFStringEncodingWindowsLatin1
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create

/**
 * Kotlin/Native ships no legacy charset tables, so detection is delegated to
 * Foundation. Each candidate encoding is applied strictly (NSString creation
 * fails on malformed input), and the first clean decode wins.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun decodeTextWithPlatformCharset(bytes: ByteArray): String? {
    if (bytes.isEmpty()) return null
    val data = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    for (encoding in IosLegacyStringEncodings) {
        val decoded = NSString.create(data = data, encoding = encoding)?.toString() ?: continue
        if ('\uFFFD' !in decoded) return decoded
    }
    return null
}

// Order matters: GB18030 is a superset of GBK/GB2312, so Simplified Chinese
// "ANSI" files must be attempted before any single-byte interpretation.
private val IosLegacyStringEncodings: List<ULong> = listOf(
    nsStringEncoding(kCFStringEncodingGB_18030_2000),
    nsStringEncoding(kCFStringEncodingGBK_95),
    nsStringEncoding(kCFStringEncodingBig5_HKSCS_1999),
    nsStringEncoding(kCFStringEncodingDOSJapanese),
    nsStringEncoding(kCFStringEncodingDOSKorean),
    // Declared as CFStringEncoding (UInt) rather than a plain enum constant.
    nsStringEncoding(kCFStringEncodingWindowsLatin1.toLong())
)

private fun nsStringEncoding(cfEncoding: Long): ULong =
    CFStringConvertEncodingToNSStringEncoding(cfEncoding.toUInt())
