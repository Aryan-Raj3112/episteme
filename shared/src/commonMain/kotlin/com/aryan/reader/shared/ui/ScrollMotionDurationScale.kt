package com.aryan.reader.shared.ui

import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Pins Compose's motion duration scale to real time for finger-launched scroll physics.
 *
 * Compose reads the system animation scale from [MotionDurationScale] in the coroutine context and,
 * when it is `0f`, plays every animation at its end value on the first frame
 * (`SuspendAnimation.doAnimationFrameWithScale` uses `anim.durationNanos` as the play time). For a
 * decay fling that means the whole deceleration is applied in a single frame: the content teleports
 * to the fling's rest position instead of gliding there. That is issue #484 - "PDF vertical
 * scrolling micro-hops" - and it only reproduces with the device's animator duration scale at 0.
 *
 * Platform scroll physics (`OverScroller`, `RecyclerView`, WebView) ignore that setting, and so does
 * Compose's own: `DefaultFlingBehavior.performFling` runs the decay inside
 * `withContext(DefaultScrollMotionDurationScale)`, whose scale factor is hard-coded to `1f`
 * (`androidx.compose.foundation.gestures.Scrollable`). Readers here drive their cameras with
 * `Animatable.animateDecay` directly instead of going through `Modifier.scrollable`, so they have to
 * pin the scale themselves or they become the only surface on the device that stops flinging when
 * "Remove animations" is enabled.
 *
 * Install this only on the coroutine that runs a fling. Decorative and programmatic animation (page
 * transitions, TTS/search centering, dialogs) must keep honouring the system setting;
 * [com.aryan.reader.shared.ReaderMotionPolicy] stays the source of truth for those.
 */
object ScrollPhysicsMotionDurationScale : MotionDurationScale {
    override val scaleFactor: Float
        get() = 1f
}

/**
 * Launches [block] as finger-launched scroll physics, decoupled from the system motion scale.
 *
 * Every fling in the readers goes through here so the pinned scale is applied in exactly one place:
 * a fling launched with a plain [launch] collapses into a single jump whenever the user has animations
 * disabled. [start] defaults to the standard dispatch; the vertical reader passes
 * [CoroutineStart.UNDISPATCHED] because a scheduling gap between pointer-up and the first decay frame
 * is visible as a stall.
 */
fun CoroutineScope.launchScrollPhysics(
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch(ScrollPhysicsMotionDurationScale, start = start, block = block)
