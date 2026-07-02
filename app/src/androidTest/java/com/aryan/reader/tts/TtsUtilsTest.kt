// TtsUtilsTest.kt
package com.aryan.reader.tts

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class TtsUtilsTest {

    @Test
    fun splitTextIntoChunks_withShortText_returnsSingleChunk() {
        val text = "This is a short sentence."
        val chunks = splitTextIntoChunks(text, 100)
        assertThat(chunks).containsExactly("This is a short sentence.")
    }

    @Test
    fun splitTextIntoChunks_withMultipleSentences_splitsCorrectly() {
        val text = "First sentence. Second sentence! Third sentence? And a fourth."
        val chunks = splitTextIntoChunks(text, 20)
        assertThat(chunks).containsExactly(
            "First sentence.",
            "Second sentence!",
            "Third sentence?",
            "And a fourth."
        ).inOrder()
    }

    @Test
    fun splitTextIntoChunks_combinesShortSentences() {
        val text = "First. Second. Third. Fourth."
        val chunks = splitTextIntoChunks(text, maxLengthPerChunk = 20)
        assertThat(chunks).containsExactly(
            "First. Second.",
            "Third. Fourth."
        ).inOrder()
    }

    @Test
    fun splitTextIntoChunks_withLongSentence_splitsOnWordsWhenNeeded() {
        val text = "This is a very long sentence that exceeds the maximum chunk length but has no punctuation to split on."
        val chunks = splitTextIntoChunks(text, 50)
        assertThat(chunks).containsExactly(
            "This is a very long sentence that exceeds the",
            "maximum chunk length but has no punctuation to",
            "split on."
        ).inOrder()
    }

    @Test
    fun splitTextIntoChunks_withEmptyText_returnsEmptyList() {
        val text = ""
        val chunks = splitTextIntoChunks(text, 100)
        assertThat(chunks).isEmpty()
    }

    @Test
    fun splitTextIntoChunks_withBlankText_returnsEmptyList() {
        val text = "   "
        val chunks = splitTextIntoChunks(text, 100)
        assertThat(chunks).isEmpty()
    }

    @Test
    fun splitTextIntoChunks_handlesAbbreviations() {
        val text = "Mr. Smith went to Washington. Dr. Jones followed."
        val chunks = splitTextIntoChunks(text, 40)
        assertThat(chunks).containsExactly(
            "Mr. Smith went to Washington.",
            "Dr. Jones followed."
        ).inOrder()
    }
}