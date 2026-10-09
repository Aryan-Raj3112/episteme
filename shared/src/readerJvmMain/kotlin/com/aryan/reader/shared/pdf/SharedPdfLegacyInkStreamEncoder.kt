package com.aryan.reader.shared.pdf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.OutputStream

/**
 * Streaming JVM encode of the legacy Android ink sidecar.
 *
 * [SharedPdfLegacyInkCodec.encode] materializes the whole payload twice: the
 * full kotlinx.serialization DOM (one [kotlinx.serialization.json.JsonObject]
 * plus three `JsonPrimitive`s per ink point) and then the serialized text. That
 * peak is what OOMs the saver on small heaps — a within-cap document can still
 * produce a DOM an order of magnitude larger than the sidecar itself, and the
 * encoder holds the DOM while `JsonToStringWriter` grows the final string.
 *
 * This writes the same bytes incrementally instead, one annotation at a time:
 * peak is bounded by the largest single annotation rather than the document.
 * Element shape and caps come from [SharedPdfLegacyInkCodec.toJsonElement], so
 * the output is byte-identical to [SharedPdfLegacyInkCodec.encode].
 */
object SharedPdfLegacyInkStreamEncoder {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Writes [annotations] as the sidecar's JSON array to [out]. The stream is
     * flushed but not closed — the caller owns it.
     */
    fun encode(annotations: List<SharedPdfLegacyInkAnnotation>, out: OutputStream) {
        out.write('['.code)
        // Same cap as encode: data past it can never be read back.
        var written = 0
        for (annotation in annotations) {
            if (written >= SharedPdfLegacyInkCodec.MAX_ANNOTATIONS_PER_LOAD) break
            if (written > 0) out.write(','.code)
            out.write(
                json.encodeToString(JsonElement.serializer(), SharedPdfLegacyInkCodec.toJsonElement(annotation))
                    .toByteArray(Charsets.UTF_8)
            )
            written++
        }
        out.write(']'.code)
        out.flush()
    }
}