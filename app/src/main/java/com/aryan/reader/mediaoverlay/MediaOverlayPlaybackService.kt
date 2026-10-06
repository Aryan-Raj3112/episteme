package com.aryan.reader.mediaoverlay

import android.app.Notification
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * The media session behind [AndroidSharedMediaOverlayPlayback].
 *
 * A sibling of `AudiobookPlaybackService` rather than a reuse, for the reason given there: this one
 * has no database, no book-id keying and no resume rewind. It exists so media-overlay narration gets
 * a lock-screen entry, transport controls, and audio-focus handling without inheriting audiobook
 * semantics that would fight a sentence-at-a-time unit of playback.
 *
 * Deliberately thin: [AndroidSharedMediaOverlayPlayback] does all the sequencing and state, so this
 * is only a host for the player plus the notification. Keeping it thin is what stops the two from
 * disagreeing about what is playing.
 */
@OptIn(UnstableApi::class)
class MediaOverlayPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // Never leave a notification behind for an idle player: a stale narration entry in the
        // shade, for a chapter that already finished, is worse than no entry.
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER)

        val player = androidx.media3.exoplayer.ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        session = MediaSession.Builder(this, player)
            .setId(MEDIA_OVERLAY_SESSION_ID)
            .setBitmapLoader(com.aryan.reader.MediaNotificationBitmapLoader(this))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Match the audiobook service: leaving the app should not strand a silent session in the
        // notification shade holding the audio focus.
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private companion object {
        /**
         * Distinct from the audiobook session id so the two never merge in the system media
         * controls — a book can be an audiobook and have media overlays at once.
         */
        const val MEDIA_OVERLAY_SESSION_ID = "reader-media-overlay-playback"
    }
}