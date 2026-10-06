package com.aryan.reader.shared.reader

/**
 * The iOS holder for media overlay playback.
 *
 * iOS has no media overlay engine yet, so this is a null-safe seam rather than a player: Android's
 * engine is the benchmark and iOS follows it, so the arbitration wiring can land first and the
 * engine plug in behind it without touching any call site.
 *
 * Nulling the holder is the "stop" from the arbiter's point of view, which is why [stop] also drops
 * the reference rather than calling through. A stale engine left installed would keep producing
 * audio after the surface that owned it was gone — the exact failure the arbiter exists to prevent,
 * and one that would be invisible until someone played a book on both platforms in one session.
 */
object IosSharedMediaOverlayPlaybackHolder {
    private var installed: SharedMediaOverlayPlayback? = null

    /** The installed engine, or null when media overlay playback is not available on iOS yet. */
    var playback: SharedMediaOverlayPlayback?
        get() = installed
        set(value) {
            installed = value
        }

    fun stop() {
        installed?.stop()
        installed = null
    }
}