package com.aryan.reader.shared.ios

import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.reader.ReaderCloudTtsTraceTag
import com.aryan.reader.shared.reader.ReaderTtsStartTag
import platform.posix.pthread_main_np
import kotlin.time.TimeMark

/**
 * Dedicated TTS-start diagnostics under the common [ReaderTtsStartTag].
 *
 * Ungated on purpose: when TTS start freezes or is slow on device, filter
 * the device console for the tag and send the lines — no diagnostics flag
 * needed. Every line carries wall-clock ms (cross-engine correlation),
 * elapsed ms since session start (where known), and the calling thread
 * (main-thread blocks are the freeze suspects).
 *
 * Volume rule: session milestones only (start, audio-session handoff,
 * first speak, first audio). Never per-chunk logging.
 */
internal fun iosTtsStartLog(stage: String, detail: String = "", elapsedSince: TimeMark? = null) {
    val elapsed = elapsedSince?.let { " +${it.elapsedNow().inWholeMilliseconds}ms" }.orEmpty()
    val thread = if (pthread_main_np() != 0) "main" else "bg"
    val nowMs = currentTimestamp()
    val extra = if (detail.isNotBlank()) " $detail" else ""
    val message = "$stage$elapsed thread=$thread t=$nowMs$extra"
    IosDiagnosticLogStore.record(ReaderTtsStartTag, message)
    println("[$ReaderTtsStartTag] $message")
}

/**
 * Dedicated iOS cloud-TTS playback trace under the common
 * [ReaderCloudTtsTraceTag].
 *
 * Ungated on purpose: when cloud playback stalls after a chunk on device,
 * filter the device console for the tag (or use in-app Export logs) and send
 * the lines — no diagnostics flag needed. Every line carries the calling
 * thread (delegate callbacks arriving off-main are suspects) and wall-clock
 * ms (cross-engine correlation with the audio-session lines).
 *
 * Privacy rule: session/chunk counters, byte sizes, HTTP statuses, player
 * positions, and booleans only. Never chunk text, audio bytes, tokens, or
 * API keys.
 */
internal fun iosCloudTtsTraceLog(stage: String, detail: String = "") {
    val thread = if (pthread_main_np() != 0) "main" else "bg"
    val nowMs = currentTimestamp()
    val extra = if (detail.isNotBlank()) " $detail" else ""
    val message = "$stage thread=$thread t=$nowMs$extra"
    IosDiagnosticLogStore.record(ReaderCloudTtsTraceTag, message)
    println("[$ReaderCloudTtsTraceTag] $message")
}
