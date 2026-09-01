package com.aryan.reader.shared.reader

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import org.mozilla.universalchardet.UniversalDetector

private const val CharsetDetectionSampleBytes = 64 * 1024

/**
 * Detects the legacy charset with juniversalchardet (Mozilla's universal
 * charset detector) and decodes leniently, so GBK/GB2312/GB18030 "ANSI"
 * exports, Big5, Shift-JIS, EUC-KR and windows-125x files all resolve.
 */
internal actual fun decodeTextWithPlatformCharset(bytes: ByteArray): String? {
    if (bytes.isEmpty()) return null
    val detectedName = detectPlatformCharsetName(bytes) ?: return null
    // Strict UTF-8 already failed upstream, so answers that decode through
    // UTF-8 cannot improve the result.
    if (detectedName.equals("UTF-8", ignoreCase = true) || detectedName.equals("US-ASCII", ignoreCase = true)) {
        return null
    }
    val charset = runCatching { Charset.forName(detectedName) }.getOrNull() ?: return null
    return runCatching {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()
}

/** juniversalchardet name detection over a bounded leading sample, shared by decode and streaming. */
internal fun detectPlatformCharsetName(bytes: ByteArray): String? {
    val sampleLength = minOf(bytes.size, CharsetDetectionSampleBytes)
    if (sampleLength == 0) return null
    val detector = UniversalDetector()
    try {
        detector.handleData(bytes, 0, sampleLength)
        detector.dataEnd()
        return detector.detectedCharset
    } finally {
        detector.reset()
    }
}
