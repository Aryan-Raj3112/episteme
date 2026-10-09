package com.aryan.reader.shared

/**
 * TTS playlist and stream bookkeeping, lifted verbatim from Android's
 * `TtsPlaybackManager.kt` so the rules have one home (parity item B10).
 *
 * Only the pure decision logic moved. Anything that needs `java.io.File`,
 * `java.nio.ByteBuffer`, or a Media3 `Player` constant stayed on Android,
 * because those are genuinely platform-bound:
 *  - `resolveWavFileDurationMs` reads a file header,
 *  - `shouldRetryPrematureTtsStreamTransition` tests `Player.MEDIA_ITEM_TRANSITION_REASON_AUTO`.
 *
 * Android remains the behavioral benchmark. Bodies here are byte-for-byte the
 * logic Android had, so this is a move, not a redesign; where a constant is
 * duplicated it is called out.
 */

/** Android benchmark: `TTS_STREAM_WAV_HEADER_BYTES`. */
const val TTS_STREAM_WAV_HEADER_BYTES = 44L

/** Android benchmark: `TTS_STREAM_PCM_BYTES_PER_MS` (48 kHz, 16-bit mono). */
const val TTS_STREAM_PCM_BYTES_PER_MS = 48L

private const val TTS_NOTIFICATION_MIN_DURATION_MS = 1_500L
private const val TTS_NOTIFICATION_TRAILING_BUFFER_MS = 2_000L
private const val TTS_NOTIFICATION_AVERAGE_WORD_MS = 550L
private const val TTS_NOTIFICATION_PUNCTUATION_PAUSE_MS = 120L

/** Android benchmark: `MAX_CHUNK_GENERATION_FAILURES`. */
const val MAX_TTS_CHUNK_GENERATION_FAILURES = 2

private val TTS_NOTIFICATION_WORD_PATTERN = Regex("""\S+""")

/**
 * Only a forward skip can reuse the playlist item already at [playlistIndex];
 * a backward skip has to seek, and a negative index is not a real item.
 */
fun resolveSharedTtsReusablePlaylistIndex(
    playlistIndex: Int?,
    direction: Int,
): Int? {
    if (direction != 1) return null
    return playlistIndex?.takeIf { it >= 0 }
}

/**
 * A chunk may join the playlist once its predecessor is already there, so the
 * playlist never ends up with a hole the auto-advance would have to skip.
 * Chunk 0 is always exposable since it has no predecessor.
 */
fun canExposeSharedTtsChunkInPlaylist(
    targetChunkIndex: Int,
    playlistChunkIds: List<Int>,
): Boolean {
    if (targetChunkIndex < 0) return false
    if (targetChunkIndex in playlistChunkIds) return true
    return targetChunkIndex == 0 || (targetChunkIndex - 1) in playlistChunkIds
}

/**
 * Where [targetChunkIndex] would be inserted to keep the playlist sorted, or
 * null when it must not be added (not exposable yet, or already present).
 */
fun resolveSharedTtsContiguousPlaylistInsertPosition(
    targetChunkIndex: Int,
    playlistChunkIds: List<Int>,
): Int? {
    if (!canExposeSharedTtsChunkInPlaylist(targetChunkIndex, playlistChunkIds)) return null
    if (targetChunkIndex in playlistChunkIds) return null
    val largerIndex = playlistChunkIds.indexOfFirst { it > targetChunkIndex }
    return if (largerIndex >= 0) largerIndex else playlistChunkIds.size
}

/**
 * A forward skip must wait while its target is still being prefetched and has
 * no playlist slot yet; otherwise the seek would land on stale audio.
 */
fun shouldWaitForSharedInFlightTtsSkip(
    direction: Int,
    isTargetPrefetching: Boolean,
    targetPlaylistIndex: Int?,
): Boolean {
    return direction == 1 && isTargetPrefetching && targetPlaylistIndex == null
}

/** Give up on a chunk after [maxFailures] attempts rather than retrying forever. */
fun shouldGiveUpSharedTtsChunkGeneration(
    failureCount: Int,
    maxFailures: Int = MAX_TTS_CHUNK_GENERATION_FAILURES,
): Boolean {
    return failureCount >= maxFailures
}

/**
 * Playback duration implied by a streamed WAV's byte length, or null when the
 * stream is too short to hold anything beyond its header.
 */
fun resolveSharedTtsStreamPcmDurationMs(totalBytes: Long): Long? {
    if (totalBytes <= TTS_STREAM_WAV_HEADER_BYTES) return null
    return ((totalBytes - TTS_STREAM_WAV_HEADER_BYTES) / TTS_STREAM_PCM_BYTES_PER_MS)
        .coerceAtLeast(1L)
}

/**
 * How long a TTS notification should stay up: long enough to cover the words
 * plus punctuation pauses, and never shorter than the current playback position
 * plus a trailing buffer, so the notification does not vanish mid-sentence.
 */
fun estimateSharedTtsNotificationDurationMs(
    text: String,
    currentPositionMs: Long = 0L,
): Long? {
    val words = TTS_NOTIFICATION_WORD_PATTERN.findAll(text).count()
    if (words == 0) return null
    val punctuationPauses = text.count { it == '.' || it == '?' || it == '!' || it == ';' || it == ':' }
    val estimatedDurationMs = words * TTS_NOTIFICATION_AVERAGE_WORD_MS +
        punctuationPauses * TTS_NOTIFICATION_PUNCTUATION_PAUSE_MS
    val playbackPositionMinimumMs = if (currentPositionMs > 0L) {
        currentPositionMs + TTS_NOTIFICATION_TRAILING_BUFFER_MS
    } else {
        TTS_NOTIFICATION_MIN_DURATION_MS
    }
    val minimumDurationMs = maxOf(TTS_NOTIFICATION_MIN_DURATION_MS, playbackPositionMinimumMs)
    return estimatedDurationMs.coerceAtLeast(minimumDurationMs)
}
