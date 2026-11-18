package com.aryan.reader.shared.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debounced draft writer behind the annotation note editors.
 *
 * The editors keep their text in local composable state and the note used to be committed by the
 * explicit Save button alone, so every other dismissal path — scrim tap, back press, and the sheet's
 * own swipe-down (a body with nothing to scroll hands the drag to the sheet) — silently dropped the
 * note. Committing per keystroke is not the answer either: a commit replaces the highlight, and both
 * readers re-serialise the book's whole annotation snapshot whenever the highlight list changes, so
 * one write per character would rewrite every annotation on every character. This is the middle
 * ground: the latest text is committed one debounce after typing pauses, and [flush] commits whatever
 * is outstanding when the editor closes.
 *
 * [initialText] counts as already committed, so opening an editor never writes back the note the
 * highlight already carries.
 *
 * Not thread-safe: an editor creates one of these per open sheet and drives it from that
 * composition's own scope, so every call and every commit lands on that scope's dispatcher.
 */
class SharedReaderNoteAutosave(
    private val scope: CoroutineScope,
    initialText: String,
    private val commit: (String) -> Unit,
) {
    private var committedText: String = initialText
    private var pendingCommit: Job? = null

    /** Records the editor's current text, committing it once typing pauses. */
    fun onTextChanged(text: String) {
        pendingCommit?.cancel()
        pendingCommit = null
        if (text == committedText) return
        pendingCommit = scope.launch {
            delay(NOTE_AUTOSAVE_DEBOUNCE_MILLIS)
            commitText(text)
        }
    }

    /**
     * Commits [text] now if it differs from what was last committed.
     *
     * Idempotent on purpose: every dismissal path (including the explicit Save, which flushes and
     * then dismisses) calls it without knowing whether the debounce already fired.
     */
    fun flush(text: String) {
        pendingCommit?.cancel()
        pendingCommit = null
        if (text == committedText) return
        commitText(text)
    }

    /**
     * Drops an outstanding draft without committing it, for a highlight or annotation that is being
     * deleted: a pending job would otherwise write a note back onto an id the host just removed.
     */
    fun discard() {
        pendingCommit?.cancel()
        pendingCommit = null
    }

    private fun commitText(text: String) {
        committedText = text
        commit(text)
    }

    private companion object {
        /**
         * Comfortably longer than the pause between words, so a sentence is one write and not one
         * per word: every commit replaces the highlight, and both readers then re-serialise the
         * book's entire annotation snapshot and re-apply it to the WebView. Nothing is at risk from
         * waiting, because closing the editor flushes instead of waiting.
         */
        const val NOTE_AUTOSAVE_DEBOUNCE_MILLIS = 1500L
    }
}
