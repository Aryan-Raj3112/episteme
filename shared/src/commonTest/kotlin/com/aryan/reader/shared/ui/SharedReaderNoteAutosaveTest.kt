package com.aryan.reader.shared.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SharedReaderNoteAutosaveTest {
    @Test
    fun `commits the latest text once typing pauses`() = runTest {
        val commits = mutableListOf<String>()
        val autosave = SharedReaderNoteAutosave(this, initialText = "") { commits += it }

        autosave.onTextChanged("H")
        advanceTimeBy(300)
        autosave.onTextChanged("Hello")
        advanceTimeBy(300)
        assertEquals(emptyList(), commits, "the draft must not be committed mid-typing")

        advanceUntilIdle()

        assertEquals(listOf("Hello"), commits)
    }

    @Test
    fun `opening an editor never writes back the note it already has`() = runTest {
        val commits = mutableListOf<String>()
        val autosave = SharedReaderNoteAutosave(this, initialText = "existing") { commits += it }

        advanceUntilIdle()
        autosave.flush("existing")
        advanceUntilIdle()

        assertEquals(emptyList(), commits)
    }

    @Test
    fun `flush commits immediately and never twice`() = runTest {
        val commits = mutableListOf<String>()
        val autosave = SharedReaderNoteAutosave(this, initialText = "") { commits += it }

        autosave.onTextChanged("half-typed note")
        autosave.flush("half-typed note")
        assertEquals(listOf("half-typed note"), commits, "dismissal must not wait for the debounce")

        // The debounce that was in flight is gone, and a second flush (Save after dismiss, or a
        // dismissal after the editor already autosaved) is a no-op.
        advanceUntilIdle()
        autosave.flush("half-typed note")
        assertEquals(listOf("half-typed note"), commits)
    }

    @Test
    fun `discard drops the pending draft`() = runTest {
        val commits = mutableListOf<String>()
        val autosave = SharedReaderNoteAutosave(this, initialText = "") { commits += it }

        autosave.onTextChanged("note for a highlight about to be deleted")
        autosave.discard()
        advanceUntilIdle()

        assertEquals(emptyList(), commits, "a deleted highlight must not get its draft written back")
    }

    @Test
    fun `reverting to the note the editor opened with is committed again`() = runTest {
        val commits = mutableListOf<String>()
        val autosave = SharedReaderNoteAutosave(this, initialText = "kept") { commits += it }

        autosave.onTextChanged("kept!")
        advanceUntilIdle()
        // The draft already replaced the stored note, so going back to the opening text is a real
        // change now and has to be written, or the highlight keeps the abandoned edit.
        autosave.onTextChanged("kept")
        advanceUntilIdle()

        assertEquals(listOf("kept!", "kept"), commits)
    }
}
