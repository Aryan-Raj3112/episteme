@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

/**
 * Audio-session activation/teardown shared by the iOS TTS engines.
 *
 * `AVAudioSession.setActive(true/false)` can block while the system
 * attaches/detaches the audio route, and the engines hit it straight from a
 * button press or a synthesizer delegate callback, which froze the UI
 * (`AVAudioSession_iOS.mm:978` warns against main-thread calls). Both
 * directions therefore run off the main thread, on a scope that outlives the
 * engine: a released reader must still hand audio back to the system, and a
 * starting session must not gate playback on route negotiation.
 *
 * Transitions are serialized through a shared generation counter because the
 * background scope runs them concurrently: a stop() teardown landing after
 * a restart's activate() would otherwise yank the route out from under the
 * new session (silence / spurious interruptions / flapping state). Last
 * writer wins; superseded transitions log and skip.
 */
internal object IosTtsAudioSessionTeardown {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Callers invoke activate/deactivate from the main thread (UI handlers,
    // engine methods, synthesizer delegate callbacks), so the increment is
    // main-confined in practice; background coroutines only read.
    @Volatile
    private var sessionGeneration = 0L

    private fun nextGeneration(): Long {
        sessionGeneration += 1
        return sessionGeneration
    }

    /**
     * Activates the shared playback session off the main thread.
     * Fire-and-forget on purpose: playback starts immediately while route
     * negotiation finishes in the background.
     */
    fun activate() {
        val generation = nextGeneration()
        iosTtsStartLog("audioSession.activate requested", "gen=$generation")
        val mark = TimeSource.Monotonic.markNow()
        scope.launch {
            if (sessionGeneration != generation) {
                iosTtsStartLog("audioSession.activate superseded", "gen=$generation current=$sessionGeneration")
                return@launch
            }
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            session.setActive(active = true, error = null)
            iosTtsStartLog("audioSession.activate done", "gen=$generation blockedMs=${mark.elapsedNow().inWholeMilliseconds}")
        }
    }

    /**
     * Deactivates the shared audio session off the main thread.
     * Unconditional variant for engines without a generation guard.
     */
    fun deactivate() {
        val generation = nextGeneration()
        iosTtsStartLog("audioSession.deactivate requested", "gen=$generation")
        val mark = TimeSource.Monotonic.markNow()
        scope.launch {
            if (sessionGeneration != generation) {
                iosTtsStartLog("audioSession.deactivate superseded", "gen=$generation current=$sessionGeneration")
                return@launch
            }
            AVAudioSession.sharedInstance().setActive(active = false, error = null)
            iosTtsStartLog("audioSession.deactivate done", "gen=$generation blockedMs=${mark.elapsedNow().inWholeMilliseconds}")
        }
    }

    /**
     * Deactivates the shared audio session unless [isStillOwner] reports that a
     * newer session has taken it over in the meantime.
     */
    fun deactivateIfStillOwner(isStillOwner: suspend () -> Boolean) {
        val generation = nextGeneration()
        iosTtsStartLog("audioSession.deactivate requested", "gen=$generation guarded")
        scope.launch {
            if (sessionGeneration != generation) {
                iosTtsStartLog("audioSession.deactivate superseded", "gen=$generation current=$sessionGeneration")
                return@launch
            }
            if (withContext(Dispatchers.Main) { isStillOwner() }) {
                AVAudioSession.sharedInstance().setActive(active = false, error = null)
                iosTtsStartLog("audioSession.deactivate done", "gen=$generation guarded")
            } else {
                iosTtsStartLog("audioSession.deactivate skipped", "gen=$generation not-owner")
            }
        }
    }
}
