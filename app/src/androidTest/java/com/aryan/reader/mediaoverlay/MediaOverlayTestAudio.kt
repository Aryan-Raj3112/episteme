package com.aryan.reader.mediaoverlay

import java.io.ByteArrayOutputStream

/**
 * PCM WAV bytes for the media-overlay instrumented tests, generated rather than committed.
 *
 * 16-bit mono at 8 kHz, carrying a low square wave **rather than silence**: a file of zero samples can
 * be optimised away by a decoder or an audio sink, and a test that passes because nothing was rendered
 * proves nothing about sequencing.
 */
internal fun mediaOverlayTestWavBytes(durationMs: Int, sampleRate: Int = 8_000): ByteArray {
    val sampleCount = durationMs * sampleRate / 1_000
    val dataBytes = sampleCount * 2
    val out = ByteArrayOutputStream(44 + dataBytes)
    fun ascii(text: String) = out.write(text.toByteArray(Charsets.US_ASCII))
    fun intLe(value: Int) = repeat(4) { shift -> out.write((value shr (8 * shift)) and 0xFF) }
    fun shortLe(value: Int) = repeat(2) { shift -> out.write((value shr (8 * shift)) and 0xFF) }

    ascii("RIFF"); intLe(36 + dataBytes); ascii("WAVE")
    ascii("fmt "); intLe(16); shortLe(1); shortLe(1)
    intLe(sampleRate); intLe(sampleRate * 2); shortLe(2); shortLe(16)
    ascii("data"); intLe(dataBytes)
    repeat(sampleCount) { index ->
        val sample = if ((index / 40) % 2 == 0) 64 else -64
        shortLe(sample and 0xFF or (if (sample < 0) 0xFF00 else 0))
    }
    return out.toByteArray()
}
