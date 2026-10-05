package com.aryan.reader.pdf

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Live fling velocity in screen px/s, plus a frame counter.
 *
 * Written on every fling frame and read by the prefetch window, so it must stay out of Compose
 * state: routing it through `mutableStateOf` would invalidate the prefetch window on every frame,
 * which is the very thing the window exists to avoid.
 *
 * The velocity lives here rather than being read back off `panXAnimatable` / `panYAnimatable`
 * because the decay runs on throwaway `Animatable` instances - those Animatables are only
 * synchronised after the decay ends, so their `velocity` was always zero during a fling and
 * `isFastFlinging` could never become true.
 */
internal class PdfVerticalFlingVelocity {
    private val xBits = AtomicInteger(0)
    private val yBits = AtomicInteger(0)
    private val frames = AtomicInteger(0)

    var x: Float
        get() = java.lang.Float.intBitsToFloat(xBits.get())
        set(value) {
            xBits.set(java.lang.Float.floatToRawIntBits(value))
        }

    var y: Float
        get() = java.lang.Float.intBitsToFloat(yBits.get())
        set(value) {
            yBits.set(java.lang.Float.floatToRawIntBits(value))
        }

    /** Incremented once per committed fling frame. */
    var frameCount: Int
        get() = frames.get()
        set(value) {
            frames.set(value)
        }

    fun clear() {
        x = 0f
        y = 0f
    }
}

/**
 * Fixed-size ring buffer of frame timings for the PDF vertical reader's scroll hot path.
 *
 * Every `PdfVerticalPerfLog` / `PdfScrollTrace` call site builds its message string **eagerly**,
 * including from inside the fling frame callback, so keeping a handle on scroll health through
 * Timber alone means paying string-concatenation cost on every frame of every gesture. Recording
 * primitives here and formatting only on dump keeps the instrumented path allocation-free.
 *
 * Sampling itself is deliberately cheap: one `System.nanoTime()` and a few integer stores.
 */
internal class PdfScrollFrameStats(private val capacity: Int = 256) {

    private companion object {
        const val KIND_FRAME = 0
        const val KIND_GESTURE_DOWN = 1
        const val KIND_GESTURE_UP = 2
        const val KIND_FLING_START = 3
        const val KIND_FLING_END = 4
        const val KIND_FLING_FRAME = 5
        const val KIND_RELEASE_TO_FIRST_FLING_FRAME = 6
        const val KIND_VISIBLE_PAGES = 7

        /** Longer than a 60Hz frame by enough to be unambiguous, shorter than a visible stall. */
        const val SLOW_FRAME_NANOS = 20_000_000L
    }

    private val nanos = LongArray(capacity)
    private val kinds = IntArray(capacity)
    private val values = FloatArray(capacity)
    private var writeIndex = 0
    private var recorded = 0

    // Aggregates, so the interesting numbers survive the ring wrapping.
    private val slowFrameCount = AtomicInteger(0)
    private val worstFrameNanos = AtomicLong(0)
    private val totalFrameNanos = AtomicLong(0)
    private val frameCount = AtomicInteger(0)
    private var lastFrameNanos = 0L

    /** Timestamp of the last gesture release, used to time the release-to-first-fling-frame gap. */
    @Volatile
    var releaseNanos: Long = 0L
        private set

    /** Time from pointer-up to the first committed fling frame. */
    @Volatile
    var releaseToFirstFlingFrameNanos: Long = 0L
        private set

    // Lifetime totals, kept separately from the per-gesture ones so `summary()` can report the
    // gesture that just happened rather than everything since launch.
    private val lifetimeFrames = AtomicInteger(0)
    private val lifetimeSlow = AtomicInteger(0)
    private val lifetimeWorstNanos = AtomicLong(0)
    private val lifetimeTotalNanos = AtomicLong(0)

    fun beginFrame(nowNanos: Long) {
        val delta = if (lastFrameNanos == 0L) 0L else nowNanos - lastFrameNanos
        lastFrameNanos = nowNanos
        if (delta <= 0L) return

        frameCount.incrementAndGet()
        totalFrameNanos.addAndGet(delta)
        if (delta > worstFrameNanos.get()) worstFrameNanos.set(delta)
        if (delta >= SLOW_FRAME_NANOS) slowFrameCount.incrementAndGet()

        lifetimeFrames.incrementAndGet()
        lifetimeTotalNanos.addAndGet(delta)
        if (delta > lifetimeWorstNanos.get()) lifetimeWorstNanos.set(delta)
        if (delta >= SLOW_FRAME_NANOS) lifetimeSlow.incrementAndGet()

        record(nowNanos, KIND_FRAME, delta / 1_000_000f)
    }

    fun endFrame() {
        lastFrameNanos = 0L
    }

    // Starts a fresh per-gesture window. Called on pointer-down so the fling-end line reports the
    // gesture that just ran rather than lifetime totals, which are useless for judging one fling.
    fun gestureDown(nowNanos: Long) {
        resetGestureWindow()
        record(nowNanos, KIND_GESTURE_DOWN, 0f)
    }

    fun gestureUp(nowNanos: Long) {
        releaseNanos = nowNanos
        record(nowNanos, KIND_GESTURE_UP, 0f)
    }

    fun flingStart(nowNanos: Long) = record(nowNanos, KIND_FLING_START, 0f)

    fun flingFrame(nowNanos: Long, velocity: Float) {
        if (releaseToFirstFlingFrameNanos == 0L && releaseNanos != 0L) {
            releaseToFirstFlingFrameNanos = (nowNanos - releaseNanos).coerceAtLeast(0L)
        }
        record(nowNanos, KIND_FLING_FRAME, velocity)
    }

    fun flingEnd(nowNanos: Long) {
        record(nowNanos, KIND_FLING_END, 0f)
        releaseNanos = 0L
        lastFrameNanos = 0L
    }

    fun visiblePages(nowNanos: Long, count: Int) = record(nowNanos, KIND_VISIBLE_PAGES, count.toFloat())

    private fun record(nowNanos: Long, kind: Int, value: Float) {
        nanos[writeIndex] = nowNanos
        kinds[writeIndex] = kind
        values[writeIndex] = value
        writeIndex = (writeIndex + 1) % capacity
        if (recorded < capacity) recorded++
    }

    /** Clears the per-gesture window only; lifetime totals survive. */
    fun resetGestureWindow() {
        writeIndex = 0
        recorded = 0
        lastFrameNanos = 0L
        releaseNanos = 0L
        releaseToFirstFlingFrameNanos = 0L
        slowFrameCount.set(0)
        worstFrameNanos.set(0)
        totalFrameNanos.set(0)
        frameCount.set(0)
    }

    /** Clears everything, lifetime totals included. */
    fun reset() {
        resetGestureWindow()
        lifetimeFrames.set(0)
        lifetimeSlow.set(0)
        lifetimeWorstNanos.set(0)
        lifetimeTotalNanos.set(0)
    }

    /**
     * One line for the gesture that just finished.
     *
     * `avgFrameMs` is the number that matters for #484: a fling that renders every frame on time has
     * an average at or under the display's frame budget (~16.7ms at 60Hz). A fling that is visibly
     * hopping shows a much higher average with a large `slowFrames` count, because each surviving
     * frame has to advance the decay by the time it actually lost.
     */
    fun summary(): String {
        val frames = frameCount.get()
        val averageMs = if (frames > 0) totalFrameNanos.get() / frames / 1_000_000f else 0f
        val gapMs = releaseToFirstFlingFrameNanos / 1_000_000f
        return "frames=$frames avgFrameMs=${PdfVerticalPerfLog.f(averageMs)} " +
            "slowFrames=${slowFrameCount.get()} worstFrameMs=${PdfVerticalPerfLog.f(worstFrameNanos.get() / 1_000_000f)} " +
            "releaseToFirstFlingFrameMs=${PdfVerticalPerfLog.f(gapMs)}"
    }

    /** Lifetime totals, for comparing sessions rather than individual flings. */
    fun lifetimeSummary(): String {
        val frames = lifetimeFrames.get()
        val averageMs = if (frames > 0) lifetimeTotalNanos.get() / frames / 1_000_000f else 0f
        return "lifetime frames=$frames avgFrameMs=${PdfVerticalPerfLog.f(averageMs)} " +
            "slowFrames=${lifetimeSlow.get()} worstFrameMs=${PdfVerticalPerfLog.f(lifetimeWorstNanos.get() / 1_000_000f)}"
    }

    /** Dumps the ring buffer in chronological order. For logcat / bug reports. */
    fun dump(): String = buildString {
        appendLine("PdfScrollFrameStats ${summary()} | ${lifetimeSummary()}")
        val start = if (recorded < capacity) 0 else writeIndex
        repeat(recorded) { offset ->
            val index = (start + offset) % capacity
            val deltaMs = nanos[index] - nanos[start]
            append("  +${PdfVerticalPerfLog.f(deltaMs / 1_000_000f)}ms ${describe(kinds[index])}")
            val value = values[index]
            if (value != 0f) append(" value=${PdfVerticalPerfLog.f(value)}")
            appendLine()
        }
    }

    private fun describe(kind: Int): String = when (kind) {
        KIND_FRAME -> "frame"
        KIND_GESTURE_DOWN -> "gesture-down"
        KIND_GESTURE_UP -> "gesture-up"
        KIND_FLING_START -> "fling-start"
        KIND_FLING_END -> "fling-end"
        KIND_FLING_FRAME -> "fling-frame"
        KIND_RELEASE_TO_FIRST_FLING_FRAME -> "release-to-first-fling-frame"
        KIND_VISIBLE_PAGES -> "visible-pages"
        else -> "unknown"
    }
}