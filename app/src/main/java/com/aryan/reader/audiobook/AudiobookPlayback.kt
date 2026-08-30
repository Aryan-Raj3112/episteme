package com.aryan.reader.audiobook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaController
import androidx.media3.session.MediaNotification
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import androidx.room.withTransaction
import com.aryan.reader.data.AppDatabase
import com.aryan.reader.logMediaTransport
import com.aryan.reader.mediaButtonKeyEventDetails
import com.aryan.reader.playerTransportSnapshot
import com.aryan.reader.shared.SharedAudiobookPlaybackRequest
import com.aryan.reader.shared.SharedAudiobookPlaybackState
import com.aryan.reader.shared.formatSharedAudiobookSleepTimer
import com.aryan.reader.shared.sharedAudiobookResumePosition
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

typealias AudiobookPlaybackState = SharedAudiobookPlaybackState
typealias AudiobookPlaybackRequest = SharedAudiobookPlaybackRequest

@OptIn(UnstableApi::class)
private class MediaTransportDiagnosticsCallback : MediaSession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        logMediaTransport(
            "audiobook-on-connect",
            "package=${controller.packageName} uid=${controller.uid} ${playerTransportSnapshot(session.player)}"
        )
        return super.onConnect(session, controller)
    }

    override fun onMediaButtonEvent(
        mediaSession: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent
    ): Boolean {
        logMediaTransport(
            "audiobook-on-media-button",
            "package=${controllerInfo.packageName} ${mediaButtonKeyEventDetails(intent)} " +
                playerTransportSnapshot(mediaSession.player)
        )
        return super.onMediaButtonEvent(mediaSession, controllerInfo, intent)
    }
}

@OptIn(UnstableApi::class)
private class LoggingAudiobookNotificationProvider(
    context: Context
) : DefaultMediaNotificationProvider(
    context,
    { _ -> AUDIOBOOK_NOTIFICATION_ID },
    AUDIOBOOK_NOTIFICATION_CHANNEL_ID,
    com.aryan.reader.R.string.audiobook_notification_channel_name
) {
    override fun addNotificationActions(
        mediaSession: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory
    ): IntArray {
        val actions = super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
        logMediaTransport(
            "audiobook-notification-actions",
            "buttons=${mediaButtons.size} actions=${actions.joinToString()} " +
                playerTransportSnapshot(mediaSession.player)
        )
        return actions
    }
}

@OptIn(UnstableApi::class)
class AudiobookPlaybackService : MediaSessionService(), Player.Listener {
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val database by lazy { AppDatabase.getDatabase(this) }
    private var saveJob: Job? = null
    private var sleepTimerJob: Job? = null
    private val diagnosticsCallback = MediaTransportDiagnosticsCallback()

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(LoggingAudiobookNotificationProvider(this))
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(this)
        session = MediaSession.Builder(this, player)
            .setId(AUDIOBOOK_MEDIA_SESSION_ID)
            .setCallback(diagnosticsCallback)
            .build()
        logMediaTransport(
            "audiobook-service-created",
            "session=reader-audiobook-playback sessionAvailable=${session != null}"
        )
        saveJob = scope.launch {
            while (isActive) {
                delay(5_000)
                persistPosition()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        logMediaTransport(
            "audiobook-on-get-session",
            "package=${controllerInfo.packageName} sessionAvailable=${session != null}"
        )
        return session
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null) {
            val keyEventDetails = if (intent.action == Intent.ACTION_MEDIA_BUTTON) {
                " ${mediaButtonKeyEventDetails(intent)}"
            } else {
                ""
            }
            logMediaTransport(
                "audiobook-on-start-command",
                "action=${intent.action} startId=$startId$keyEventDetails ${playerTransportSnapshot(player)}"
            )
        }
        when (intent?.action) {
            ACTION_AUDIOBOOK_STOP -> {
                sleepTimerJob?.cancel()
                sleepTimerJob = null

                scope.launch {
                    persistPosition()
                    player.stop()
                    player.clearMediaItems()
                    stopSelf()
                }
            }

            ACTION_AUDIOBOOK_SLEEP_TIMER -> {
                sleepTimerJob?.cancel()
                sleepTimerJob = null

                val minutes = intent.getIntExtra(
                    EXTRA_AUDIOBOOK_SLEEP_MINUTES,
                    0
                )

                if (minutes > 0) {
                    sleepTimerJob = scope.launch {
                        var remainingSeconds = minutes * 60
                        while (remainingSeconds > 0) {
                            delay(1_000)
                            remainingSeconds = com.aryan.reader.shared.advanceSharedSleepTimer(remainingSeconds, player.isPlaying)
                        }

                        persistPosition()

                        sleepTimerJob = null
                        player.stop()
                        player.clearMediaItems()
                        stopSelf()
                    }
                }
            }

            ACTION_AUDIOBOOK_CANCEL_SLEEP_TIMER -> {
                sleepTimerJob?.cancel()
                sleepTimerJob = null
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        logMediaTransport(
            "audiobook-is-playing-changed",
            "isPlaying=$isPlaying ${playerTransportSnapshot(player)}"
        )
        if (!isPlaying) scope.launch { persistPosition() }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) scope.launch { persistPosition(completed = true) }
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        logMediaTransport(
            "audiobook-on-update-notification",
            "startInForegroundRequired=$startInForegroundRequired ${playerTransportSnapshot(player)}"
        )
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    private suspend fun persistPosition(completed: Boolean = false) {
        if (!::player.isInitialized || player.mediaItemCount == 0) return
        val bookId = player.currentMediaItem?.mediaId?.takeIf(String::isNotBlank) ?: return
        val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: return
        val position = if (completed) duration else player.currentPosition.coerceIn(0L, duration)
        val speed = player.playbackParameters.speed
        val progress = position.toFloat() / duration.toFloat() * 100f
        database.withTransaction {
            database.audiobookDao().updatePlayback(bookId, position, speed)
            database.recentFileDao().updateAudiobookPosition(bookId, progress, System.currentTimeMillis())
        }
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        if (!player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        logMediaTransport("audiobook-service-destroyed", "sessionAlive=${session != null}")
        saveJob?.cancel()
        sleepTimerJob?.cancel()
        if (::player.isInitialized) {
            player.release()
        }
        session?.release()
        session = null
        scope.cancel()
        super.onDestroy()
    }
}

internal const val AUDIOBOOK_MEDIA_SESSION_ID = "reader-audiobook-playback"
internal const val ACTION_AUDIOBOOK_STOP = "com.aryan.reader.audiobook.STOP"
internal const val ACTION_AUDIOBOOK_SLEEP_TIMER = "com.aryan.reader.audiobook.SLEEP_TIMER"
internal const val ACTION_AUDIOBOOK_CANCEL_SLEEP_TIMER = "com.aryan.reader.audiobook.CANCEL_SLEEP_TIMER"
internal const val EXTRA_AUDIOBOOK_SLEEP_MINUTES = "sleep_minutes"
private const val AUDIOBOOK_NOTIFICATION_ID = 1002
private const val AUDIOBOOK_NOTIFICATION_CHANNEL_ID = "audiobook_playback"

@OptIn(UnstableApi::class)
class AudiobookController(context: Context) : Player.Listener {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingBook: AudiobookPlaybackRequest? = null
    private var pollJob: Job? = null
    private val _state = MutableStateFlow(AudiobookPlaybackState())
    val state = _state.asStateFlow()
    private val _sleepTimerLabel = MutableStateFlow("Sleep")
    val sleepTimerLabel = _sleepTimerLabel.asStateFlow()
    private var sleepCountdownJob: Job? = null
    private var sleepTimerRemainingMs: Long = 0L

    fun connect(book: AudiobookPlaybackRequest) {
        pendingBook = book
        controller?.let { loadIfNeeded(it, book); return }
        connectSession()
    }

    fun connectSession() {
        if (controller != null) return
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, AudiobookPlaybackService::class.java))
        val building = MediaController.Builder(context, token).buildAsync()
        future = building
        building.addListener({
            runCatching { building.get() }.onSuccess { connected ->
                if (future !== building) { connected.release(); return@onSuccess }
                future = null
                controller = connected
                connected.addListener(this)
                _state.value = _state.value.copy(connected = true)
                pendingBook?.let { loadIfNeeded(connected, it) }
                startPolling()
            }.onFailure { _state.value = _state.value.copy(error = it.message, isLoading = false) }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun loadIfNeeded(controller: MediaController, book: AudiobookPlaybackRequest) {
        if (controller.currentMediaItem?.mediaId == book.bookId) { updateState(); return }
        _state.value = _state.value.copy(isLoading = true, error = null)
        val metadata = MediaMetadata.Builder()
            .setTitle(book.title)
            .setArtist(book.author ?: book.narrator)
            .setAlbumTitle(book.album)
            .setArtworkUri(book.coverPath?.let { Uri.fromFile(File(it)) })
            .build()
        controller.setMediaItem(
            MediaItem.Builder().setMediaId(book.bookId).setUri(Uri.fromFile(File(book.filePath))).setMediaMetadata(metadata).build(),
            audiobookResumePosition(book.positionMs)
        )
        controller.setPlaybackSpeed(book.speed)
        controller.prepare()
        updateState()
    }

    fun togglePlay(onBeforePlay: () -> Unit = {}) {
        controller?.let { if (it.isPlaying) it.pause() else { onBeforePlay(); it.play() } }
    }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs.coerceAtLeast(0L)) }
    fun seekBy(deltaMs: Long) { controller?.let { seekTo(it.currentPosition + deltaMs) } }
    fun setSpeed(speed: Float) { controller?.setPlaybackSpeed(speed) }
    fun stop() {
        sleepCountdownJob?.cancel()
        sleepCountdownJob = null
        sleepTimerRemainingMs = 0L
        _sleepTimerLabel.value = "Sleep"
        _state.value = _state.value.copy(sleepTimerRemainingMs = 0L)

        context.startService(
            Intent(context, AudiobookPlaybackService::class.java)
                .setAction(ACTION_AUDIOBOOK_STOP)
        )
    }

    fun toggleSleepTimer(minutes: Int = 30) {
        if (sleepCountdownJob != null) {
            sleepCountdownJob?.cancel()
            sleepCountdownJob = null
            sleepTimerRemainingMs = 0L
            _sleepTimerLabel.value = "Sleep"
            _state.value = _state.value.copy(sleepTimerRemainingMs = 0L)
            context.startService(Intent(context, AudiobookPlaybackService::class.java).setAction(ACTION_AUDIOBOOK_CANCEL_SLEEP_TIMER))
            return
        }
        context.startService(Intent(context, AudiobookPlaybackService::class.java).apply {
            action = ACTION_AUDIOBOOK_SLEEP_TIMER
            putExtra(EXTRA_AUDIOBOOK_SLEEP_MINUTES, minutes)
        })
        sleepCountdownJob = scope.launch {
            var remaining = minutes * 60
            while (remaining > 0) {
                sleepTimerRemainingMs = remaining * 1_000L
                _sleepTimerLabel.value = formatSleepTimerLabel(remaining)
                _state.value = _state.value.copy(sleepTimerRemainingMs = sleepTimerRemainingMs)
                delay(1_000)
                remaining = com.aryan.reader.shared.advanceSharedSleepTimer(remaining, _state.value.isPlaying)
            }
            sleepTimerRemainingMs = 0L
            _sleepTimerLabel.value = "Sleep"
            _state.value = _state.value.copy(sleepTimerRemainingMs = 0L)
            sleepCountdownJob = null
        }
    }

    override fun onEvents(player: Player, events: Player.Events) = updateState()
    override fun onPlayerError(error: PlaybackException) { _state.value = _state.value.copy(error = error.message, isLoading = false) }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch { while (isActive) { updateState(); delay(500) } }
    }

    private fun updateState() {
        val player = controller ?: return
        _state.value = AudiobookPlaybackState(
            connected = true,
            bookId = player.currentMediaItem?.mediaId,
            isPlaying = player.isPlaying,
            isLoading = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L,
            speed = player.playbackParameters.speed,
            sleepTimerRemainingMs = sleepTimerRemainingMs,
            error = player.playerError?.message,
        )
    }

    fun release() {
        pollJob?.cancel()
        sleepCountdownJob?.cancel()
        controller?.removeListener(this)
        controller?.release()
        controller = null
        future?.cancel(true)
        future = null
        scope.cancel()
    }
}

internal fun audiobookResumePosition(savedPositionMs: Long, rewindMs: Long = 10_000L): Long =
    sharedAudiobookResumePosition(savedPositionMs, rewindMs)

internal fun formatSleepTimerLabel(remainingSeconds: Int): String =
    formatSharedAudiobookSleepTimer(remainingSeconds)
