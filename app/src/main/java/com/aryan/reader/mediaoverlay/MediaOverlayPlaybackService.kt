package com.aryan.reader.mediaoverlay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.aryan.reader.MediaButtonRouting
import com.aryan.reader.R
import com.aryan.reader.logMediaTransport
import com.aryan.reader.pinPostedPlaybackNotification
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

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
 *
 * **It owns the archive.** The audio is streamed out of the EPUB rather than extracted to disk, and
 * the player lives here while the reader's UI lives in a composable — so the `ZipFile` has to cross
 * that boundary. It crosses as a *path*, via [ATTACH_BOOK_COMMAND], and the service opens and closes
 * it. Sending an open handle instead would mean two `ZipFile`s over one file for no benefit, and
 * whichever the service failed to close would keep a descriptor alive until the process died.
 */
/**
 * Gives narration its own notification identity.
 *
 * [DefaultMediaNotificationProvider] would post this session under Media3's default id `1001`,
 * which is the same id [com.aryan.reader.tts.TtsService] uses for its foreground notification. Two
 * live sessions sharing one notification id means whichever posts last silently replaces the
 * other's notification — so a listener could start narration and lose the TTS control, or the
 * reverse. A dedicated id (and channel) keeps both in the shade at once, which is exactly why the
 * two services were kept separate rather than folded together.
 */
@OptIn(UnstableApi::class)
private class MediaOverlayNotificationProvider(
    context: Context
) : DefaultMediaNotificationProvider(
    context,
    { _ -> MEDIA_OVERLAY_NOTIFICATION_ID },
    MEDIA_OVERLAY_NOTIFICATION_CHANNEL_ID,
    R.string.media_overlay_notification_channel_name
) {
    override fun addNotificationActions(
        mediaSession: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory
    ): IntArray {
        val actions = super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
        logMediaTransport(
            "media-overlay-notification-actions",
            "buttons=${mediaButtons.size} actions=${actions.joinToString()}"
        )
        return actions
    }
}

@OptIn(UnstableApi::class)
class MediaOverlayPlaybackService : MediaSessionService(), Player.Listener {
    private var session: MediaSession? = null
    private var player: ExoPlayer? = null

    /**
     * The book narration is streamed from.
     *
     * A field rather than a local because the player is built once, in [onCreate], and its data
     * source factory has to reach a book that is not known until playback starts. See the holder.
     */
    private val archive = SharedMediaOverlayArchiveHolder()

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(MediaOverlayNotificationProvider(this))
        // Never leave a notification behind for an idle player: a stale narration entry in the
        // shade, for a chapter that already finished, is worse than no entry.
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER)

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this).setDataSourceFactory(archive.dataSourceFactory(this))
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer.addListener(this)

        session = MediaSession.Builder(this, exoPlayer)
            .setId(MEDIA_OVERLAY_SESSION_ID)
            .setBitmapLoader(com.aryan.reader.MediaNotificationBitmapLoader(this))
            .setCallback(MediaOverlaySessionCallback())
            .build()
        player = exoPlayer
    }

    private inner class MediaOverlaySessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(ATTACH_BOOK_COMMAND)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand != ATTACH_BOOK_COMMAND) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }
            val path = args.getString(KEY_ATTACH_BOOK_PATH)
            val error = if (path.isNullOrBlank()) {
                "No book file for narration"
            } else {
                archive.attach(File(path))
            }
            return Futures.immediateFuture(
                SessionResult(
                    if (error == null) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_IO,
                    Bundle().apply { if (error != null) putString(KEY_ATTACH_BOOK_ERROR, error) }
                )
            )
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Remembers this service as the media-button target once narration actually makes a sound.
     *
     * [com.aryan.reader.MediaButtonRoutingReceiver] has to pick one service for a media button that
     * arrives when no session is alive, and it picks the last one that was playing. Without this the
     * receiver would route a headset resume after a process death back to TTS.
     */
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            MediaButtonRouting.recordPlaybackService(this, MediaOverlayPlaybackService::class.java)
        }
    }

    /**
     * Media3 posts a swipe-dismissible notification; re-posting it as ongoing while narration is
     * active is what stops a swipe from stranding a session that keeps playing silently. Same fix,
     * and same reasoning, as [com.aryan.reader.tts.TtsService].
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired)
        pinPostedPlaybackNotification(
            context = this,
            notificationId = MEDIA_OVERLAY_NOTIFICATION_ID,
            playWhenReady = session.player.playWhenReady,
            playbackState = session.player.playbackState,
            diagnosticsTag = MEDIA_OVERLAY_DIAG_TAG
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Match the audiobook service: leaving the app should not strand a silent session in the
        // notification shade holding the audio focus.
        val current = player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        archive.close()
        player?.removeListener(this)
        player?.release()
        player = null
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        /**
         * Distinct from the audiobook session id so the two never merge in the system media
         * controls — a book can be an audiobook and have media overlays at once.
         */
        const val MEDIA_OVERLAY_SESSION_ID = "reader-media-overlay-playback"

        internal const val KEY_ATTACH_BOOK_PATH = "reader.mediaoverlay.bookPath"
        internal const val KEY_ATTACH_BOOK_ERROR = "reader.mediaoverlay.error"

        internal val ATTACH_BOOK_COMMAND =
            SessionCommand("com.aryan.reader.mediaoverlay.ATTACH_BOOK", Bundle.EMPTY)
    }
}

/** Distinct from Media3's default `1001` (TTS) and the audiobook's `1002`; see the provider above. */
private const val MEDIA_OVERLAY_NOTIFICATION_ID = 1003
private const val MEDIA_OVERLAY_NOTIFICATION_CHANNEL_ID = "media_overlay_playback"
private const val MEDIA_OVERLAY_DIAG_TAG = "MEDIA_OVERLAY_DIAG"

