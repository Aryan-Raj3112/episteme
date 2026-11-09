package com.aryan.reader.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioural coverage for issue #484: a decay fling must survive the system animator duration scale
 * being 0 ("Remove animations"), which is what the reporter had enabled.
 *
 * Compose derives the play time of every animation from `MotionDurationScale` in the coroutine
 * context and, at `0f`, jumps straight to the animation's end value - so an unpinned fling applies
 * its whole deceleration on the first frame. These tests drive frames by hand, so they assert frame
 * counts deterministically instead of depending on wall-clock timing or a device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScrollPhysicsMotionDurationScaleTest {

    @Test
    fun pinnedFlingRunsFrameByFrameUnderZeroMotionScale() = runTest {
        val clock = ManualFrameClock()
        val scope = CoroutineScope(
            UnconfinedTestDispatcher(testScheduler) + clock + ZeroMotionDurationScale
        )
        val frames = FlingFrames()

        // The pinned helper must override the zero scale already present in the scope's context.
        scope.launchScrollPhysics {
            Animatable(0f).animateDecay(FLING_VELOCITY, DECAY) {
                frames.count++
                frames.last = value
            }
        }
        clock.runFrames()

        assertTrue(
            frames.count > 5,
            "a pinned fling must advance over many frames, ran ${frames.count}",
        )
        assertTrue(frames.last > 0f, "a pinned fling must travel, ended at ${frames.last}")
    }

    /**
     * The control case: the same decay on a plain [launch] collapses to one frame, which is exactly
     * the jump the reporter saw. If Compose ever stops collapsing it, this test fails and the pin can
     * be revisited - the assertion above still keeps the pinned path honest either way.
     */
    @Test
    fun unpinnedFlingCollapsesUnderZeroMotionScale() = runTest {
        val clock = ManualFrameClock()
        val scope = CoroutineScope(
            UnconfinedTestDispatcher(testScheduler) + clock + ZeroMotionDurationScale
        )
        val frames = FlingFrames()

        scope.launch {
            Animatable(0f).animateDecay(FLING_VELOCITY, DECAY) {
                frames.count++
                frames.last = value
            }
        }
        clock.runFrames()

        assertEquals(1, frames.count, "an unpinned decay must collapse to its end value")
        assertTrue(frames.last > 0f, "the collapsed fling still lands on the decay target")
    }

    /** Pinning changes pacing only: both paths settle on the same distance. */
    @Test
    fun pinnedFlingTravelsTheSameDistanceAsTheCollapsedOne() = runTest {
        val pinned = FlingFrames()
        val unpinned = FlingFrames()

        listOf(true, false).forEach { shouldPin ->
            val clock = ManualFrameClock()
            val scope = CoroutineScope(
                UnconfinedTestDispatcher(testScheduler) + clock + ZeroMotionDurationScale
            )
            val frames = if (shouldPin) pinned else unpinned
            val launchFling: suspend CoroutineScope.() -> Unit = {
                Animatable(0f).animateDecay(FLING_VELOCITY, DECAY) {
                    frames.count++
                    frames.last = value
                }
            }
            if (shouldPin) scope.launchScrollPhysics(block = launchFling)
            else scope.launch(block = launchFling)
            clock.runFrames()
        }

        assertEquals(unpinned.last, pinned.last, 1f)
    }

    private class FlingFrames {
        var count = 0
        var last = 0f
    }

    private object ZeroMotionDurationScale : MotionDurationScale {
        override val scaleFactor: Float get() = 0f
    }

    private companion object {
        /** Comfortably above Compose's minimum fling velocity, so the decay is long-lived. */
        const val FLING_VELOCITY = 6_000f
        val DECAY = exponentialDecay<Float>(frictionMultiplier = 2f)
        const val FRAME_STEP_NANOS = 16_666_667L
        /** Enough frames for any decay spec; the loop exits as soon as nothing is waiting. */
        const val MAX_FRAMES = 2_000
    }

    /**
     * Hands out frame ticks only when the test asks, which makes "how many frames did this fling
     * commit" an exact number rather than a timing accident.
     */
    private class ManualFrameClock : MonotonicFrameClock {
        private var pending: (() -> Unit)? = null
        private var timeNanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
            suspendCoroutine { continuation ->
                pending = {
                    timeNanos += FRAME_STEP_NANOS
                    continuation.resume(onFrame(timeNanos))
                }
            }

        fun runFrames() {
            var frames = 0
            while (pending != null && frames < MAX_FRAMES) {
                val frame = pending ?: return
                pending = null
                frame()
                frames++
            }
            check(pending == null) { "fling did not settle within $MAX_FRAMES frames" }
        }
    }
}
