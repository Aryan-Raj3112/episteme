@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Transitions are serialized behind [mutationLock] and ordered by a shared
 * generation counter. The generation check used to sit outside the mutation, so
 * a teardown that had already passed its check could still land *after* the
 * next session's activation and yank the route out from under it — audible as
 * read-aloud going silent exactly at a chapter hand-off, where the engines
 * deactivate and re-activate in quick succession. Holding the lock across the
 * check and the `setActive` call means the newest transition is always the one
 * that ends up applied, and [activateAndWait] lets a caller that is about to
 * start playback wait for the route instead of racing it.
 */
internal object IosTtsAudioSessionTeardown {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Serializes the `AVAudioSession` mutations; never acquired on the main thread. */
    private val mutationLock = Mutex()

    // Callers invoke activate/deactivate from the main thread (UI handlers,
    // engine methods, synthesizer delegate callbacks), so the increment is
    // main-confined in practice; background coroutines only read.
    @Volatile
    private var sessionGeneration = 0L

    private fun nextGeneration(): Long {
        sessionGeneration += 1
        return sessionGeneration
    }

    private suspend fun apply(
        generation: Long,
        label: String,
        isStillOwner: (suspend () -> Boolean)? = null,
        mutate: () -> Unit,
    ) {
        mutationLock.withLock {
            if (sessionGeneration != generation) {
                iosTtsStartLog("audioSession.$label superseded", "gen=$generation current=$sessionGeneration")
                return
            }
            if (isStillOwner != null && !withContext(Dispatchers.Main) { isStillOwner() }) {
                iosTtsStartLog("audioSession.$label skipped", "gen=$generation not-owner")
                return
            }
            mutate()
        }
    }

    /**
     * Activates the shared playback session off the main thread.
     * Fire-and-forget: for callers that are not about to render audio yet.
     */
    fun activate() {
        val generation = nextGeneration()
        iosTtsStartLog("audioSession.activate requested", "gen=$generation")
        scope.launch { activateNow(generation) }
    }

    /**
     * Activates the session and waits for the system to finish route negotiation.
     *
     * Playback started before this returns can be silently dropped: `AVAudioPlayer.play()` on an
     * inactive session reports failure and `AVSpeechSynthesizer` never delivers its delegate
     * callbacks, which is indistinguishable from the user pressing stop. Engines await this
     * before starting a chapter.
     */
    suspend fun activateAndWait() {
        val generation = nextGeneration()
        iosTtsStartLog("audioSession.activate requested", "gen=$generation awaited")
        withContext(Dispatchers.Default) { activateNow(generation) }
    }

    private suspend fun activateNow(generation: Long) {
        val mark = TimeSource.Monotonic.markNow()
        apply(generation, "activate") {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            session.setActive(active = true, error = null)
            iosTtsStartLog(
                "audioSession.activate done",
                "gen=$generation blockedMs=${mark.elapsedNow().inWholeMilliseconds}"
            )
        }
    }

    /**
     * Deactivates the shared audio session off the main thread.
     * Unconditional variant for engines without an owner predicate.
     */
    fun deactivate() {
        val generation = nextGeneration()
        iosTtsStartLog("audioSession.deactivate requested", "gen=$generation")
        val mark = TimeSource.Monotonic.markNow()
        scope.launch {
            apply(generation, "deactivate") {
                AVAudioSession.sharedInstance().setActive(active = false, error = null)
                iosTtsStartLog(
                    "audioSession.deactivate done",
                    "gen=$generation blockedMs=${mark.elapsedNow().inWholeMilliseconds}"
                )
            }
        }
    }

    /**
     * Deactivates the shared audio session unless [isStillOwner] reports that a
     * newer session has taken it over in the meantime.
     */
    fun deactivateIfStillOwner(isStillOwner: suspend () -> Boolean) {
        val generation = nextGeneration()
        iosTtsStartLog("audioSession.deactivate requested", "gen=$generation guarded")
        val mark = TimeSource.Monotonic.markNow()
        scope.launch {
            apply(generation, "deactivate", isStillOwner = isStillOwner) {
                AVAudioSession.sharedInstance().setActive(active = false, error = null)
                iosTtsStartLog(
                    "audioSession.deactivate done",
                    "gen=$generation guarded blockedMs=${mark.elapsedNow().inWholeMilliseconds}"
                )
            }
        }
    }
}
