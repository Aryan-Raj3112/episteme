package com.aryan.reader.tts

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.aryan.reader.R
import com.aryan.reader.logMediaTransport
import com.aryan.reader.playerTransportSnapshot

/**
 * Keeps a silent, looping audio stream active in this app's own process while direct-local TTS
 * is speaking.
 *
 * Android's TextToSpeech engine renders speech inside the engine app's process, so the OS
 * attributes the playback to the engine's uid and never to this app. Since Android 12 the
 * media button session is elected as "the media session of the most recently audio-playing
 * uid" (MediaSessionStack.updateMediaButtonSessionIfNeeded), which means headset play/pause
 * buttons are never routed to the TTS session. This anchor puts this app's uid into the audio
 * playback monitor, so the election finds our session.
 *
 * The stream must contain real digital silence (not a zero-volume player): some OEM audio
 * stacks treat muted players as inactive. It must not request audio focus or react to
 * becoming-noisy: focus is owned by [DirectLocalTtsAudioFocusManager].
 */
@UnstableApi
internal class DirectLocalTtsPlaybackAnchor(context: Context) {
    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null

    fun start() {
        val player = player ?: buildPlayer().also { player = it }
        if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING) return
        player.seekTo(0)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
        logMediaTransport("tts-anchor-start", playerTransportSnapshot(player))
    }

    fun pause() {
        val player = player ?: return
        if (!player.isPlaying && player.playbackState != Player.STATE_BUFFERING) return
        player.pause()
        logMediaTransport("tts-anchor-pause", playerTransportSnapshot(player))
    }

    fun release() {
        player?.let {
            logMediaTransport("tts-anchor-release", playerTransportSnapshot(it))
            it.release()
        }
        player = null
    }

    private fun buildPlayer(): ExoPlayer {
        val player = ExoPlayer.Builder(appContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ false
            )
            .setHandleAudioBecomingNoisy(false)
            .build()
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.setMediaItem(
            MediaItem.fromUri(RawResourceDataSource.buildRawResourceUri(R.raw.tts_silence_anchor))
        )
        player.prepare()
        return player
    }
}
