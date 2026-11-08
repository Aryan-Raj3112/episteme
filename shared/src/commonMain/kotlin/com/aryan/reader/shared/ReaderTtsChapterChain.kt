package com.aryan.reader.shared

/**
 * Chapter chaining for read-aloud: what a chapter that finished naturally does next.
 *
 * Both platforms used to treat "the plan for the next chapter came back empty" as the
 * end of the book and quietly stop the session. That branch is reachable without the book
 * being over — iOS plans the next chapter from the paginated `pages` list, so a plan that
 * runs while pagination is still catching up returns nothing, and Android's paginated path
 * returns null when the next chapter cannot be paginated yet. Because a chapter boundary is
 * also where the audio session/focus is handed over, giving up there looked exactly like
 * "read-aloud stops when it changes chapter" (intermittently, depending on whether the plan
 * was ready in time).
 *
 * The rule here keeps that distinction: only "there is no next chapter" ends the session.
 * "There are more chapters but nothing could be planned" is retried, and only reported as
 * the end once the retry budget is spent.
 */

/** How many times a chapter hand-off is re-planned before the chain is considered done. */
const val READER_TTS_CHAIN_MAX_ATTEMPTS = 2

/** Gap before re-planning a chapter hand-off, long enough for pagination to settle. */
const val READER_TTS_CHAIN_RETRY_DELAY_MS = 500L

sealed interface ReaderTtsChainDecision {
    /** Speak [chapterIndex] in a continued session. */
    data class Start(val chapterIndex: Int) : ReaderTtsChainDecision

    /** Nothing was planned yet, but the book is not over: re-plan after [delayMillis]. */
    data class Retry(val delayMillis: Long) : ReaderTtsChainDecision

    /** No chapter after the completed one: the session is done. */
    data object EndOfBook : ReaderTtsChainDecision
}

/**
 * Decides the hand-off after [completedChapterIndex] finished.
 *
 * @param plannedChapterIndex first chapter after the completed one that produced chunks, or
 *   null when planning produced nothing (not the same thing as the book ending).
 * @param attempt how many times this hand-off was already re-planned (0 on the first try).
 */
fun decideReaderTtsChain(
    completedChapterIndex: Int?,
    chapterCount: Int,
    plannedChapterIndex: Int?,
    attempt: Int = 0,
    maxAttempts: Int = READER_TTS_CHAIN_MAX_ATTEMPTS,
    retryDelayMillis: Long = READER_TTS_CHAIN_RETRY_DELAY_MS,
): ReaderTtsChainDecision {
    if (completedChapterIndex == null || chapterCount <= 0) return ReaderTtsChainDecision.EndOfBook
    if (plannedChapterIndex != null && plannedChapterIndex in 0 until chapterCount) {
        return ReaderTtsChainDecision.Start(plannedChapterIndex)
    }
    if (completedChapterIndex + 1 >= chapterCount) return ReaderTtsChainDecision.EndOfBook
    if (attempt >= maxAttempts.coerceAtLeast(1)) return ReaderTtsChainDecision.EndOfBook
    return ReaderTtsChainDecision.Retry(retryDelayMillis)
}

/**
 * Seconds of grace before an idle read-aloud session releases the audio session it keeps
 * across a chapter hand-off. The hand-off happens within a few hundred milliseconds of the
 * completion, so anything longer than that is a user who stopped listening.
 */
const val READER_TTS_AUDIO_SESSION_HANDOFF_GRACE_MS = 5_000L
