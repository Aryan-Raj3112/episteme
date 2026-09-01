package com.aryan.reader.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TtsLifecycleSourceTest {

    @Test
    fun `main view model does not eagerly connect the tts service`() {
        val source = sourceFile("com/aryan/reader/MainViewModel.kt").readText()

        assertTrue(source.contains("val ttsController by lazy { TtsController(appContext) }"))
        assertFalse(source.contains("TtsController(appContext).apply { connect() }"))
    }

    @Test
    fun `direct local engine initializes only when speech is requested`() {
        val source = sourceFile("com/aryan/reader/tts/DirectLocalTtsPlayer.kt").readText()

        assertFalse(source.contains("init {\n        initialize()"))
        assertTrue(source.contains("if (!initialized) {\n            initialize()"))
        assertTrue(source.contains("tts?.speak(request.text, request.queueMode"))
        assertFalse(source.contains("synthesizeToFile"))
    }

    @Test
    fun `audiobook tab does not bind tts until a play action`() {
        val source = sourceFile("com/aryan/reader/UnifiedLibraryScreen.kt").readText()

        assertFalse(source.contains("LaunchedEffect(viewModel.ttsController) { viewModel.ttsController.connect() }"))
        assertTrue(
            Regex("if \\(shouldStart\\) \\{\\s*viewModel\\.ttsController\\.connect\\(\\)")
                .containsMatchIn(source)
        )
    }

    @Test
    fun `explicit stop shuts down the local engine before service destruction`() {
        val source = sourceFile("com/aryan/reader/tts/TtsPlaybackManager.kt").readText()
        val stopBody = source.substringAfter("private fun handleStopTts")
            .substringBefore("fun stopForAppTaskRemoval")

        assertTrue(stopBody.contains("if (userInitiated) directLocalTtsPlayer.shutdown()"))
        assertTrue(source.contains("fun stopBookListeningSession()"))
    }

    @Test
    fun `direct local notification metadata uses a valid synthetic media uri`() {
        val source = sourceFile("com/aryan/reader/tts/TtsPlaybackManager.kt").readText()
        val updateLocalMediaItemBody = source.substringAfter("private fun updateLocalMediaItem")
            .substringBefore("override fun onReady")

        assertTrue(updateLocalMediaItemBody.contains(".setUri(\"tts-local://chunk/\$chunkIndex\")"))
        assertTrue(updateLocalMediaItemBody.contains(".setMediaMetadata(metadata)"))
    }

    @Test
    fun `direct local state is bridged independently of dormant exoplayer state`() {
        val managerSource = sourceFile("com/aryan/reader/tts/TtsPlaybackManager.kt").readText()
        val stateButtonBody = managerSource.substringAfter("private fun createStateButton")
            .substringBefore("private fun createStopCommandButton")
        assertTrue(stateButtonBody.contains("putBoolean(\"isPlaying\", state.isPlaying)"))
        assertTrue(stateButtonBody.contains("if (isDirectLocalPlayback()) localPlaybackState()"))

        val controllerSource = sourceFile("com/aryan/reader/tts/TtsController.kt").readText()
        val updateStateBody = controllerSource.substringAfter("private fun updateStateFromController")
            .substringBefore("fun setPlaybackParameters")
        assertTrue(updateStateBody.contains("customState.getBoolean(\"isPlaying\", false)"))
        assertTrue(updateStateBody.contains("customState.getInt(\"playbackState\", controller.playbackState)"))
        assertTrue(updateStateBody.contains("isPlaying = effectiveIsPlaying"))
        assertTrue(updateStateBody.contains("playbackState = effectivePlaybackState"))
    }

    @Test
    fun `direct local media session emits real state changes without a timeline`() {
        val serviceSource = sourceFile("com/aryan/reader/tts/TtsService.kt").readText()
        val sessionPlayerBody = serviceSource.substringAfter("private class TtsSessionPlayer")
            .substringBefore("class TtsService")

        assertTrue(sessionPlayerBody.contains("fun invalidateDirectLocalState()"))
        assertTrue(sessionPlayerBody.contains("EVENT_PLAYBACK_STATE_CHANGED"))
        assertTrue(sessionPlayerBody.contains("EVENT_PLAY_WHEN_READY_CHANGED"))
        assertTrue(sessionPlayerBody.contains("EVENT_IS_PLAYING_CHANGED"))
        assertTrue(sessionPlayerBody.contains("if (isDirectLocalPlayback()) return C.TIME_UNSET"))
        assertTrue(sessionPlayerBody.contains(".remove(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)"))

        val managerSource = sourceFile("com/aryan/reader/tts/TtsPlaybackManager.kt").readText()
        val metadataBody = managerSource.substringAfter("private fun updateLocalMediaItem")
            .substringBefore("override fun onReady")
        assertTrue(metadataBody.contains(".setArtworkUri(coverImageUri?.toUri())"))
    }

    @Test
    fun `local speed and pitch are sent before immediately restarting speech`() {
        val controlsSource = sourceFile("com/aryan/reader/epubreader/EpubReaderControls.kt").readText()
        val saveAndApplyBody = controlsSource.substringAfter("val saveAndApply = {")
            .substringBefore("Surface(")
        assertTrue(saveAndApplyBody.contains("ttsController.setPlaybackParameters(rate, pitch)"))
        assertFalse(saveAndApplyBody.contains("ttsController.sliceAndRetainPosition()"))

        val managerSource = sourceFile("com/aryan/reader/tts/TtsPlaybackManager.kt").readText()
        val parameterCommandBody = managerSource.substringAfter("SET_PLAYBACK_PARAMS_COMMAND -> {")
            .substringBefore("SKIP_TO_PREVIOUS_TTS_CHUNK_COMMAND")
        assertTrue(parameterCommandBody.contains("localSpeechRate = speed"))
        assertTrue(parameterCommandBody.contains("localSpeechPitch = pitch"))
        assertTrue(parameterCommandBody.contains("restartLocalSpeechFromCurrentPosition()"))
    }

    @Test
    fun `task removal stops playback engine and service`() {
        val source = sourceFile("com/aryan/reader/tts/TtsService.kt").readText()
        val taskRemovalBody = source.substringAfter("override fun onTaskRemoved")
            .substringBefore("override fun onGetSession")

        assertTrue(taskRemovalBody.contains("playbackManager.stopForAppTaskRemoval()"))
        assertTrue(taskRemovalBody.contains("stopSelf()"))

        val managerSource = sourceFile("com/aryan/reader/tts/TtsPlaybackManager.kt").readText()
        assertTrue(managerSource.contains("directLocalTtsPlayer.shutdown()"))
    }

    @Test
    fun `direct local speech requests audio focus before the engine speaks`() {
        val source = sourceFile("com/aryan/reader/tts/TtsPlaybackManager.kt").readText()
        val startLocalChunkBody = source.substringAfter("private fun startLocalChunk")
            .substringBefore("private fun enqueueLocalLookahead")

        assertTrue(startLocalChunkBody.contains("directLocalAudioFocusManager.requestFocus()"))
        assertTrue(
            startLocalChunkBody.indexOf("directLocalAudioFocusManager.requestFocus()") <
                startLocalChunkBody.indexOf("directLocalTtsPlayer.speak(")
        )
        assertTrue(startLocalChunkBody.contains("publishLocalChunkState(chunkIndex, isLoading = false, isPlaying = false)"))
    }

    @Test
    fun `stopping direct local tts abandons audio focus`() {
        val source = sourceFile("com/aryan/reader/tts/TtsPlaybackManager.kt").readText()
        val stopBody = source.substringAfter("private fun handleStopTts")
            .substringBefore("fun stopForAppTaskRemoval")

        assertTrue(stopBody.contains("directLocalAudioFocusManager.abandonFocus()"))

        val finishBody = source.substringAfter("private fun handleLocalChunkDone")
            .substringBefore("override fun onError")
        assertTrue(finishBody.contains("directLocalAudioFocusManager.abandonFocus()"))

        val releaseBody = source.substringAfter("fun release()")
            .substringBefore("override fun onPlaybackStateChanged")
        assertTrue(releaseBody.contains("directLocalAudioFocusManager.abandonFocus()"))
    }

    @Test
    fun `focus interruptions route through the shared interruption policy`() {
        val source = sourceFile("com/aryan/reader/tts/TtsPlaybackManager.kt").readText()

        assertTrue(source.contains("LocalTtsInterruptionEvent.Began(playbackWasActive"))
        assertTrue(source.contains("LocalTtsInterruptionEvent.Ended(systemAllowsResume = canResume)"))
        assertTrue(source.contains("LocalTtsInterruptionEvent.OutputBecameNoisy(playbackWasActive"))
        assertTrue(source.contains("LocalTtsInterruptionAction.PAUSE") || source.contains("LocalTtsInterruptionAction.RESUME"))
    }

    @Test
    fun `playback anchor runs only while local speech is active`() {
        val managerSource = sourceFile("com/aryan/reader/tts/TtsPlaybackManager.kt").readText()

        val startLocalChunkBody = managerSource.substringAfter("private fun startLocalChunk")
            .substringBefore("private fun enqueueLocalLookahead")
        assertTrue(startLocalChunkBody.contains("directLocalPlaybackAnchor.start()"))
        assertTrue(
            "anchor must start after focus is granted",
            startLocalChunkBody.indexOf("directLocalAudioFocusManager.requestFocus()") <
                startLocalChunkBody.indexOf("directLocalPlaybackAnchor.start()")
        )

        val pauseBody = managerSource.substringAfter("private fun pauseLocalSpeech")
            .substringBefore("fun stopFromTransport")
        assertTrue(pauseBody.contains("directLocalPlaybackAnchor.pause()"))

        val stopBody = managerSource.substringAfter("private fun handleStopTts")
            .substringBefore("fun stopForAppTaskRemoval")
        assertTrue(stopBody.contains("directLocalPlaybackAnchor.pause()"))

        val releaseBody = managerSource.substringAfter("fun release()")
            .substringBefore("override fun onPlaybackStateChanged")
        assertTrue(releaseBody.contains("directLocalPlaybackAnchor.release()"))

        val anchorSource = sourceFile("com/aryan/reader/tts/DirectLocalTtsPlaybackAnchor.kt").readText()
        assertTrue(anchorSource.contains("/* handleAudioFocus = */ false"))
        assertTrue(anchorSource.contains("Player.REPEAT_MODE_ONE"))
    }

    @Test
    fun `last playback service is recorded for media button resumption`() {
        val managerSource = sourceFile("com/aryan/reader/tts/TtsPlaybackManager.kt").readText()
        assertTrue(managerSource.contains("MediaButtonRouting.recordPlaybackService(appContext, TtsService::class.java.name)"))

        val audiobookSource = sourceFile("com/aryan/reader/audiobook/AudiobookPlayback.kt").readText()
        assertTrue(audiobookSource.contains("AudiobookPlaybackService::class.java"))
    }

    @Test
    fun `session control updates hop to the main looper before mutating the media session`() {
        val managerSource = sourceFile("com/aryan/reader/tts/TtsPlaybackManager.kt").readText()
        val updateControlsBody = managerSource.substringAfter("private fun updateSessionControls")
            .substringBefore("private fun createCustomLayout")

        // Media3's MediaSessionCompat playback state updater reads the custom layout fields on
        // the main looper while setCustomLayout/setMediaButtonPreferences mutate them on the
        // caller thread. A non-main caller shrinking the list between the size and get reads
        // crashes the session with IndexOutOfBoundsException, so the mutation must be marshalled.
        assertTrue(
            updateControlsBody.contains("if (Looper.myLooper() != Looper.getMainLooper())")
        )
        assertTrue(
            updateControlsBody.contains("scope.launch(Dispatchers.Main) { updateSessionControls(state) }")
        )
        assertTrue(
            "session mutations must only run after the main-thread hop",
            updateControlsBody.indexOf("Looper.myLooper() != Looper.getMainLooper()") <
                updateControlsBody.indexOf("session.setMediaButtonPreferences")
        )
    }

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("app/src/main/java/$relativePath")
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Unable to locate $relativePath from ${File(".").absolutePath}")
    }
}
