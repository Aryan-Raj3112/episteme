package com.aryan.reader.audiobook

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import androidx.room.withTransaction
import com.aryan.reader.data.AppDatabase
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

data class AudiobookPlaybackState(
    val connected: Boolean = false,
    val bookId: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val error: String? = null,
)

data class AudiobookPlaybackRequest(
    val bookId: String,
    val filePath: String,
    val title: String,
    val author: String?,
    val narrator: String?,
    val album: String?,
    val coverPath: String?,
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float,
)

@OptIn(UnstableApi::class)
class AudiobookPlaybackService : MediaSessionService(), Player.Listener {
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val database by lazy { AppDatabase.getDatabase(this) }
    private var saveJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { _ -> AUDIOBOOK_NOTIFICATION_ID },
                AUDIOBOOK_NOTIFICATION_CHANNEL_ID,
                com.aryan.reader.R.string.audiobook_notification_channel_name
            )
        )
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
            .build()
        saveJob = scope.launch {
            while (isActive) {
                delay(5_000)
                persistPosition()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying) scope.launch { persistPosition() }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) scope.launch { persistPosition(completed = true) }
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
        saveJob?.cancel()
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

    fun connect(book: AudiobookPlaybackRequest) {
        pendingBook = book
        controller?.let { loadIfNeeded(it, book); return }
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
            book.positionMs
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
            error = player.playerError?.message,
        )
    }

    fun release() {
        pollJob?.cancel()
        controller?.removeListener(this)
        controller?.release()
        controller = null
        future?.cancel(true)
        future = null
        scope.cancel()
    }
}
