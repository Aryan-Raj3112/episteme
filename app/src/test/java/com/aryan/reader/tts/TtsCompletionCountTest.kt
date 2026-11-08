package com.aryan.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Chapter chaining is driven by this counter, so the rule that makes it one event per
 * naturally finished chapter is worth pinning: both completion detectors and the
 * direct-local utterance listener report the same chapter end.
 */
class TtsCompletionCountTest {
    @Test
    fun `a natural completion advances the counter`() {
        assertEquals(1L, nextTtsCompletionCount(current = 0L, alreadyFinished = false))
        assertEquals(4L, nextTtsCompletionCount(current = 3L, alreadyFinished = false))
    }

    @Test
    fun `repeated reports of the same completion do not advance it`() {
        val afterFirst = nextTtsCompletionCount(current = 2L, alreadyFinished = false)
        assertEquals(afterFirst, nextTtsCompletionCount(current = afterFirst, alreadyFinished = true))
        assertEquals(afterFirst, nextTtsCompletionCount(current = afterFirst, alreadyFinished = true))
    }

    @Test
    fun `a continued session keeps counting from the carried value`() {
        val completedChapter = nextTtsCompletionCount(current = 7L, alreadyFinished = false)
        assertEquals(8L, completedChapter)
        // handleStartTts(continueSession = true) resets sessionFinished and carries the count,
        // so the next chapter's completion is still a new event for every watcher.
        assertEquals(9L, nextTtsCompletionCount(current = completedChapter, alreadyFinished = false))
    }
}
