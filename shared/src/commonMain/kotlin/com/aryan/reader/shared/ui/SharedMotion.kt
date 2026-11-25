package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * Material 3 motion tokens and the transitions built from them.
 *
 * The settings surface used to hard-cut between pages (swapping them was a `when`), while the host
 * animated the screen itself with the navigation library's defaults: a 700ms fade, and a
 * `scaleOut(0.7f)` for the predictive-back pop, which is the "shrinks into the middle and
 * disappears" exit. Both are motion problems, so both are solved from one place: hosts import these
 * instead of hard-coding `tween(200)` per call site, and Compose honours the platform "remove
 * animations" setting automatically because every spec is a normal animation spec.
 *
 * Four schemes, each for a different meaning:
 * - [sharedAxisSlide] — hierarchical movement inside a surface (settings list → category → detail).
 *   Content comes from the reading-direction end and leaves towards the start, so "back" is visibly
 *   the reverse.
 * - [sharedScreenEnter] / [sharedScreenExit] — route changes between opaque screens. A rigid
 *   full-width slide, so a screen never disappears while part of it is still on screen.
 * - [sharedFadeThrough] — lateral movement between unrelated states (page ↔ search results).
 *   Nothing moves, so nothing claims a spatial relationship that does not exist.
 * - [sharedReducedMotionSwap] — a cross-fade for platforms that ask for reduced motion.
 */
object SharedMotion {
    /** M3 `short2`: the quickest exit we use. */
    const val DurationShort2 = 100

    /** M3 `short4`: exits and small state changes. */
    const val DurationShort4 = 200

    /** M3 `medium2`: entrances and page changes. */
    const val DurationMedium2 = 300

    /** Distance an incoming settings page travels, as a fraction of its width. */
    const val EnterDistanceFraction = 0.14f

    /** Distance an outgoing settings page travels. Smaller than the entrance so the push reads as depth. */
    const val ExitDistanceFraction = 0.04f

    /**
     * The incoming page waits for the outgoing one to clear before it fades in. The two pages carry
     * dense, different text; letting them cross-fade at the same time showed both at once for
     * ~150ms, which reads as a double exposure rather than a page change.
     */
    const val EnterFadeDelayMillis = 140

    /** Fade-through: the outgoing state is gone quickly, the incoming one arrives after it. */
    const val FadeThroughOutMillis = 90
    const val FadeThroughInMillis = 210
    const val FadeThroughDelayMillis = 90

    /** M3 `emphasized.decelerate` — entrances come in fast and settle. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** M3 `standard.decelerate` — fades that arrive. */
    val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** M3 `standard.accelerate` — fades that leave. */
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** M3 `standard` — colour/state changes that are neither an entrance nor an exit. */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/**
 * Signed horizontal direction for a hierarchical change.
 *
 * `forward = true` means the target is deeper than the source. In LTR a deeper page enters from the
 * right and the shallower one leaves to the left; RTL mirrors both. One sign drives the entrance and
 * the exit so they can never disagree.
 */
private fun sharedAxisSign(rtl: Boolean, forward: Boolean): Float =
    if (forward != rtl) 1f else -1f

/** Enters from the reading-direction end when [forward], from the start when going back. */
fun AnimatedContentTransitionScope<*>.sharedAxisEnter(
    rtl: Boolean,
    forward: Boolean,
    distanceFraction: Float = SharedMotion.EnterDistanceFraction
): EnterTransition {
    val sign = sharedAxisSign(rtl, forward)
    return slideInHorizontally(
        animationSpec = tween(SharedMotion.DurationMedium2, easing = SharedMotion.EmphasizedDecelerate)
    ) { width -> (width * distanceFraction * sign).toInt() } +
        fadeIn(
            animationSpec = tween(
                durationMillis = SharedMotion.DurationMedium2 - SharedMotion.EnterFadeDelayMillis,
                delayMillis = SharedMotion.EnterFadeDelayMillis,
                easing = SharedMotion.StandardDecelerate
            )
        )
}

/** Leaves towards the start when [forward], towards the end when going back. */
fun AnimatedContentTransitionScope<*>.sharedAxisExit(
    rtl: Boolean,
    forward: Boolean,
    distanceFraction: Float = SharedMotion.ExitDistanceFraction
): ExitTransition {
    val sign = -sharedAxisSign(rtl, forward)
    return slideOutHorizontally(
        animationSpec = tween(SharedMotion.DurationShort4, easing = SharedMotion.StandardAccelerate)
    ) { width -> (width * distanceFraction * sign).toInt() } +
        fadeOut(
            animationSpec = tween(
                durationMillis = SharedMotion.DurationShort2,
                easing = SharedMotion.StandardAccelerate
            )
        )
}

/**
 * Hierarchical page change.
 *
 * No `SizeTransform`: a settings page is not a container that grows, it is a different page. Letting
 * the container animate its height makes the list jump while two pages are both laid out.
 */
fun AnimatedContentTransitionScope<*>.sharedAxisSlide(rtl: Boolean, forward: Boolean): ContentTransform =
    (sharedAxisEnter(rtl, forward) togetherWith sharedAxisExit(rtl, forward)).using(null)

/** Lateral change between unrelated states: out in 90ms, in after it, nothing slides. */
fun AnimatedContentTransitionScope<*>.sharedFadeThrough(): ContentTransform =
    (
        fadeIn(
            animationSpec = tween(
                durationMillis = SharedMotion.FadeThroughInMillis,
                delayMillis = SharedMotion.FadeThroughDelayMillis,
                easing = SharedMotion.StandardDecelerate
            )
        ) togetherWith fadeOut(
            animationSpec = tween(
                durationMillis = SharedMotion.FadeThroughOutMillis,
                easing = SharedMotion.StandardAccelerate
            )
        )
    ).using(null)

/**
 * Reduced-motion variant: the shortest possible cross-fade, no spatial movement.
 *
 * Compose honours the Android animator scale by itself, but iOS and desktop do not expose one to
 * shared code, so hosts pass their platform policy in and the page swap calls this instead. Keeping
 * the fade (rather than a hard cut) still shows that the content changed.
 */
fun AnimatedContentTransitionScope<*>.sharedReducedMotionSwap(): ContentTransform =
    (
        fadeIn(
            animationSpec = tween(
                durationMillis = SharedMotion.DurationShort2,
                easing = SharedMotion.StandardDecelerate
            )
        ) togetherWith fadeOut(
            animationSpec = tween(
                durationMillis = SharedMotion.DurationShort2,
                easing = SharedMotion.StandardAccelerate
            )
        )
    ).using(null)

/**
 * Route-level (whole screen) entrance: slides the full width, so at the last frame the outgoing
 * screen is completely off-screen instead of being dropped while it is still partly visible.
 *
 * Both ends of a route change use the same duration and easing, which makes the two opaque screens
 * move as one surface. A parallax (one screen moving less than the other) only works when the
 * back layer is opaque too, and route screens are opaque, so the pair has to stay rigid or the
 * background shows through the seam.
 */
fun AnimatedContentTransitionScope<*>.sharedScreenEnter(rtl: Boolean, forward: Boolean): EnterTransition {
    val sign = sharedAxisSign(rtl, forward)
    return slideInHorizontally(
        animationSpec = tween(SharedMotion.DurationMedium2, easing = SharedMotion.EmphasizedDecelerate)
    ) { width -> (width * sign).toInt() }
}

/** Route-level exit: the mirror of [sharedScreenEnter], same duration and easing. */
fun AnimatedContentTransitionScope<*>.sharedScreenExit(rtl: Boolean, forward: Boolean): ExitTransition {
    val sign = -sharedAxisSign(rtl, forward)
    return slideOutHorizontally(
        animationSpec = tween(SharedMotion.DurationMedium2, easing = SharedMotion.EmphasizedDecelerate)
    ) { width -> (width * sign).toInt() }
}
