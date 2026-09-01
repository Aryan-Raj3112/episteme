package com.aryan.reader.shared.pdf

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeToSequence
import java.io.BufferedInputStream
import java.io.InputStream

/**
 * Streaming JVM decode of the legacy Android ink sidecar. The sidecar is parsed
 * one array element at a time via kotlinx.serialization's streaming decoder, so
 * reading a huge annotation file never materializes the whole document (or its
 * DOM) in memory the way [SharedPdfLegacyInkCodec.decode] does.
 *
 * Malformed, truncated, or non-array input yields an empty result, matching the
 * all-or-nothing behavior of the String-based decode.
 */
@OptIn(ExperimentalSerializationApi::class)
object SharedPdfLegacyInkStreamDecoder {
    private const val READ_BUFFER_BYTES = 64 * 1024

    private val json = Json { ignoreUnknownKeys = true }

    fun decode(stream: InputStream, newId: () -> String): SharedPdfLegacyInkDecodeResult {
        return runCatching {
            BufferedInputStream(stream, READ_BUFFER_BYTES).use { buffered ->
                val elements = json.decodeToSequence<JsonElement>(buffered, DecodeSequenceMode.ARRAY_WRAPPED)
                SharedPdfLegacyInkCodec.decodeElements(elements, newId)
            }
        }.getOrElse { SharedPdfLegacyInkDecodeResult(emptyList()) }
    }
}
