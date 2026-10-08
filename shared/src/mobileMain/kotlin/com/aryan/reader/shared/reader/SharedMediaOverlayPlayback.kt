package com.aryan.reader.shared.reader

import kotlin.math.roundToInt

/**
 * The playback contract for EPUB media overlays, and the platform-free decisions behind it.
 *
 * A platform engine owns the audio; it does not own the *sequencing*. Everything that can be wrong
 * in a way the user would hear — which clips are playable, where a clip really ends, what comes next
 * — is a pure function here, so Android and iOS cannot disagree about it. That matters more than it
 * sounds: two platforms advancing differently is a highlight that drifts from the voice, which is
 * the one bug this whole feature exists to avoid.
 *
 * The engines differ in mechanism and must agree in behaviour:
 *
 * - **Android** builds one ExoPlayer `MediaItem` per clip with a clipping configuration, so
 *   `onMediaItemTransition` *is* the active-fragment signal — no polling, no drift.
 * - **iOS** has one `AVPlayer` and seeks per clip, advancing from a periodic time observer and the
 *   item-ended notification. `AVPlayerItem` has no clipping, so the bound is enforced here.
 */

/** Where playback is, and how it is going. */
data class SharedMediaOverlayPlaybackState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    /** The spine item being narrated, or null when nothing is loaded. */
    val spineItemIndex: Int? = null,
    /** Index into the loaded chapter's clip list — a *play* index, not the clip's source index. */
    val clipIndex: Int = 0,
    /**
     * The player clock, in milliseconds: absolute within the media file.
     *
     * Absolute rather than within-clip, because that is what both players report and what the bounds
     * below are measured against — [clipProgress] subtracts [clipStartMs] to get through the clip.
     */
    val positionMs: Long = 0L,
    /** The active clip's `clipBegin`, so a UI can show absolute file time. */
    val clipStartMs: Long = 0L,
    /** The active clip's clamped end, or null when it runs to the end of the media. */
    val clipEndMs: Long? = null,
    val speed: Float = SharedMediaOverlayDefaultSpeed,
    val bookTitle: String? = null,
    val narrator: String? = null,
    /** The book's whole runtime, when the OPF declared one. */
    val totalDurationMs: Long? = null,
    /** Non-null when playback failed; cleared by the next [SharedMediaOverlayPlayback.play]. */
    val error: String? = null,
) {
    val hasBook: Boolean get() = spineItemIndex != null

    /** How far through the active clip playback is, 0f..1f, or 0f when the clip has no known end. */
    val clipProgress: Float
        get() {
            val end = clipEndMs ?: return 0f
            val span = end - clipStartMs
            if (span <= 0L) return 0f
            return ((positionMs - clipStartMs).toFloat() / span).coerceIn(0f, 1f)
        }
}

const val SharedMediaOverlayDefaultSpeed = 1f

/** Speed limits, matching the audiobook player's own range. */
val SharedMediaOverlaySpeedRange = 0.5f..3.0f

/**
 * The speeds the narration bar offers, slowest first.
 *
 * A ladder rather than a slider: narration is listened to at two or three speeds in practice, and a
 * menu of known values is one tap with a stable label, where a drag lands on 1.13× and then reads
 * differently on each platform's number formatting. Every entry is inside
 * [SharedMediaOverlaySpeedRange], which is what the engine clamps to anyway, so the UI can never offer
 * a speed the engine would refuse.
 */
val SharedMediaOverlaySpeeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

/**
 * A speed as the reader sees it, e.g. `1.25×`.
 *
 * Written by hand because common code has no number formatting, and because `String.format` per
 * platform would print `1.00×` on one and `1,00×` on another. Trailing zeros are trimmed: a button that
 * reads `2.00×` is noise, and `2×` is what a listener says out loud. Unsupported values are clamped
 * first, so a bad restored speed shows the speed that will actually play.
 */
fun sharedMediaOverlaySpeedLabel(speed: Float): String {
    val hundredths = (sharedMediaOverlaySpeed(speed) * 100f).roundToInt()
    val whole = hundredths / 100
    val fraction = hundredths % 100
    val text = when {
        fraction == 0 -> "$whole"
        fraction % 10 == 0 -> "$whole.${fraction / 10}"
        fraction < 10 -> "$whole.0$fraction"
        else -> "$whole.$fraction"
    }
    return "$text×"
}

/**
 * Position of a source clip index in a loaded clip list, or -1 when that clip is not in it.
 *
 * The one answer, because the two numbers genuinely differ and an engine holds the list rather than
 * the plan it came from. `sharedMediaOverlayPlaybackPlan` **drops** clips — a TTS `par`, a `par` with
 * no audio — and each surviving entry keeps the index it had in the *document*, so position 1 in the
 * loaded list can be source clip 2. An engine that used a reported clip index as a position would
 * freeze at the first dropped clip: the highlight would stick to one line while the voice moved on.
 *
 * -1 rather than 0 for a clip that is not loaded, so a caller cannot mistake "absent" for "the first
 * clip" and jump the reader to the top of a chapter.
 */
fun List<SharedMediaOverlayClip>.playbackIndexOfSourceClip(clipIndex: Int): Int =
    indexOfFirst { it.clipIndex == clipIndex }

/**
 * What to play, in what order.
 *
 * Built by [sharedMediaOverlayPlaybackPlan] so both platforms filter identically. A clip the plan
 * drops is one no engine will ever be asked to play, which keeps a bad clip from stalling the
 * sequence instead of being skipped by each engine's own logic.
 */
data class SharedMediaOverlayPlaybackPlan(
    /** Playable clips in playback order. Indices are the *source* clip indices, not positions here. */
    val entries: List<SharedMediaOverlayClip>,
    /** Source clip indices matching [entries], so a UI can map back to the document. */
    val sourceClipIndices: List<Int>,
    /** Whole-runtime sum of the playable clips, or null when unbounded clips make it unknown. */
    val totalDurationMs: Long?,
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    /** Position in [entries] for a source clip index, or -1 when that clip was dropped. */
    fun playbackIndexOf(clipIndex: Int): Int = entries.playbackIndexOfSourceClip(clipIndex)

    fun clipAt(playbackIndex: Int): SharedMediaOverlayClip? = entries.getOrNull(playbackIndex)

    /** The clip after [playbackIndex], or null at the end. Never wraps: "next" at the last clip ends. */
    fun nextAfter(playbackIndex: Int): Int? = (playbackIndex + 1).takeIf { it in entries.indices }

    /**
     * The clip before [playbackIndex], or null at the start.
     *
     * No rewind-within-clip, unlike the audiobook player. An overlay clip is one sentence or one
     * line, so "previous" that restarts the current clip is a near no-op the user cannot feel;
     * stepping to the previous clip is what they meant.
     */
    fun previousBefore(playbackIndex: Int): Int? =
        (playbackIndex - 1).takeIf { it >= 0 && it < entries.size }
}

/**
 * Decides which clips are playable and how long each really runs.
 *
 * A clip is dropped when it cannot produce audio at a sane position, and the reasons are all things
 * real books contain:
 *
 * - **No audio element** — a TTS `par`. Playable only by a speech engine, which this is not, and
 *   letting it through would stall the sequence at a silent item.
 * - **An unresolvable audio path** — the file is missing from the archive or the `src` is remote.
 * - **A zero or negative length** after clamping, which would leave the player parked.
 * - **`clipBegin` past the end of the media**, so there is nothing left to play.
 *
 * @param mediaDurationMsByPath archive path -> the audio file's real duration, where known. Absent
 *   entries mean "unknown", not "zero": a missing duration leaves the clip's declared bounds alone
 *   rather than collapsing it.
 */
fun sharedMediaOverlayPlaybackPlan(
    document: SharedMediaOverlayDocument,
    mediaDurationMsByPath: Map<String, Long> = emptyMap()
): SharedMediaOverlayPlaybackPlan {
    val entries = mutableListOf<SharedMediaOverlayClip>()
    val indices = mutableListOf<Int>()
    var total = 0L
    var totalKnown = true

    for (clip in document.clips) {
        val audioPath = clip.audioPath?.takeIf { it.isNotBlank() } ?: continue
        val mediaDurationMs = mediaDurationMsByPath[audioPath]
        val endMs = sharedMediaOverlayClipEndMs(clip, mediaDurationMs)
        val lengthMs = (endMs ?: Long.MAX_VALUE) - clip.clipBeginMs
        if (lengthMs <= 0L) continue
        if (mediaDurationMs != null && clip.clipBeginMs >= mediaDurationMs) continue

        entries += clip
        indices += clip.clipIndex
        when {
            endMs == null -> totalKnown = false
            mediaDurationMs == null -> totalKnown = false
            else -> total += lengthMs
        }
    }

    return SharedMediaOverlayPlaybackPlan(
        entries = entries,
        sourceClipIndices = indices,
        totalDurationMs = if (totalKnown) total.takeIf { entries.isNotEmpty() } else null
    )
}

/**
 * Where a clip really ends, in milliseconds from the start of its audio.
 *
 * `RS §9.2.2`: a missing `clipEnd` means "to the end of the media", and a `clipEnd` past the end of
 * the media *is* the end of the media. Both matter, and getting either wrong desyncs the highlight
 * from the voice:
 *
 * - Treating a missing `clipEnd` as 0 would make the clip unplayable.
 * - Treating it as unbounded when the media duration is known would let the next clip start late,
 *   because the player would run past the end of the file into silence.
 *
 * With an unknown media duration the declared `clipEnd` is kept as-is. That is the honest answer:
 * this code does not decode audio, so it cannot do better, and clamping against an assumed zero
 * would silence the book.
 *
 * @return the clamped end, or null when the clip genuinely runs to the end of an unknown-length file.
 */
fun sharedMediaOverlayClipEndMs(
    clip: SharedMediaOverlayClip,
    mediaDurationMs: Long?
): Long? {
    val declared = clip.clipEndMs
    val duration = mediaDurationMs?.takeIf { it > 0L }
    if (duration == null) return declared
    val end = declared ?: return duration
    return minOf(end, duration)
}

/** Clamps a requested speed into the supported range; a NaN or infinite request resets to default. */
fun sharedMediaOverlaySpeed(raw: Float): Float =
    if (raw.isNaN() || raw.isInfinite()) SharedMediaOverlayDefaultSpeed else raw.coerceIn(SharedMediaOverlaySpeedRange)

/**
 * Where playback should start when the user taps play mid-chapter.
 *
 * The first clip at or after the reader's current text position wins, so tapping play while reading
 * starts the narration from where they are rather than from the top of the chapter. Falls back to
 * the last playable clip when the reader is past the end.
 *
 * @param readerClipIndex a **document** clip index, as the reader's position is expressed.
 * @return a **playback** index into [SharedMediaOverlayPlaybackPlan.entries], which is not the same
 *   number once any clip has been dropped. Returning the document index here is a bug that only
 *   appears when a clip is unplayable — i.e. on a book with a broken `src`, not on a clean one.
 */
fun sharedMediaOverlayStartPlaybackIndex(
    plan: SharedMediaOverlayPlaybackPlan,
    readerClipIndex: Int?
): Int {
    if (plan.isEmpty) return 0
    if (readerClipIndex == null) return 0
    return plan.sourceClipIndices.indexOfFirst { it >= readerClipIndex }
        .takeIf { it >= 0 }
        ?: plan.entries.lastIndex
}

/**
 * The spine items narration should continue into after [finishedSpineItemIndex], in reading order.
 *
 * A narrated book is a sequence, and a reader who lets the narration run expects it to keep going
 * rather than stop at the end of every chapter — the expectation an audiobook sets. Which item comes
 * next is a fact about the package, so it is decided here once rather than by each engine.
 *
 * [spineItemsInReadingOrder] is the *reader's* order, not the spine array's: a book whose content
 * documents were split at TOC fragments has several reader chapters per spine item, and a book with a
 * non-linear spine need not ascend. The caller builds it from its chapter list, deduplicated.
 *
 * The whole tail is returned rather than one item so a caller can skip an overlay whose SMIL turns
 * out to be unparseable — the OPF is a promise about intent, not about the file — without this
 * function having to read anything.
 *
 * Empty at the end of a narrated run, when every item after this one is un-narrated, and when the
 * finished item is not in the order at all (a book that was replaced under a live session). All three
 * mean the same thing to a caller: nothing left to narrate, stop.
 *
 * Un-narrated spine items in between are skipped rather than treated as a stop: a narrated book with
 * a cover, a colophon and a playlist between its chapters is the normal shape, and stopping at the
 * first one would make continuation almost never fire.
 */
fun sharedMediaOverlaySpineItemsAfter(
    index: SharedMediaOverlayIndex,
    spineItemsInReadingOrder: List<Int>,
    finishedSpineItemIndex: Int
): List<Int> {
    if (spineItemsInReadingOrder.isEmpty()) return emptyList()
    val position = spineItemsInReadingOrder.indexOf(finishedSpineItemIndex)
    if (position < 0) return emptyList()
    return spineItemsInReadingOrder
        .subList(position + 1, spineItemsInReadingOrder.size)
        .filter { index.smilPathBySpineItem.containsKey(it) }
}