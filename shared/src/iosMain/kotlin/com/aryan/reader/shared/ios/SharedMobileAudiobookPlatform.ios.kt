package com.aryan.reader.shared.ios

import com.aryan.reader.shared.SharedAudiobookPlaybackRequest

/**
 * Kotlin facade over the native AVPlayer controller that lives in the iOS app
 * (AudiobookPlayerController.swift). AVFoundation is not exposed through this
 * toolchain's Kotlin/Native bindings, so all playback is driven through the
 * bridge handlers registered by the Swift host. The current playback snapshot
 * is exposed to Compose directly through [ReaderIosBridge.audiobookPlaybackSnapshot].
 */
class IosAudiobookPlayback(private val bridge: ReaderIosBridge) {
    fun connect(request: SharedAudiobookPlaybackRequest) {
        stop()
        bridge.markAudiobookConnected(request.bookId)
        bridge.audiobookPlayHandler?.invoke(
            request.filePath,
            request.positionMs.toDouble(),
            request.speed.toDouble(),
        )
    }

    fun togglePlayPause() {
        if (bridge.audiobookPlaybackSnapshot.isPlaying) {
            bridge.audiobookPauseHandler?.invoke()
        } else {
            bridge.audiobookSpeedAndResumeHandler?.invoke(
                bridge.audiobookPlaybackSnapshot.speed.takeIf { it > 0f } ?: 1f,
            )
        }
    }

    fun seekTo(positionMs: Long) {
        bridge.audiobookSeekHandler?.invoke(positionMs.coerceAtLeast(0L).toDouble())
    }

    fun setSpeed(speed: Float) {
        bridge.audiobookSpeedHandler?.invoke((speed.takeIf { it > 0f } ?: 1f).toDouble())
    }

    fun setSleepTimer(minutes: Int) {
        bridge.audiobookSleepTimerHandler?.invoke(minutes.coerceAtLeast(1))
    }

    fun cancelSleepTimer() {
        bridge.audiobookCancelSleepHandler?.invoke()
    }

    fun stop() {
        bridge.audiobookStopHandler?.invoke()
        // The native controller publishes its final AVPlayer position during
        // stop. Persist that snapshot before clearing the bridge session.
        bridge.persistCurrentAudiobookPosition()
        bridge.markAudiobookStopped()
    }

    fun extractAudiobookMetadata(
        filePath: String,
        fallbackTitle: String,
        onResult: (title: String, author: String?, album: String?, durationMs: Long) -> Unit,
    ) {
        bridge.audiobookMetadataHandler?.invoke(filePath, fallbackTitle, onResult)
    }
}
