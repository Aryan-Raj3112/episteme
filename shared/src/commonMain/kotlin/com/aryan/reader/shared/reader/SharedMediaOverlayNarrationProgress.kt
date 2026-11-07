package com.aryan.reader.shared.reader

/**
 * Where a narrated book's playback stopped, in the package's own coordinates.
 *
 * Narration is not the text reading position, even though the reader follows one with the other. A
 * clip is played for a couple of seconds while the page holding it stays put, so the saved *text*
 * position is page-granular at best. Worse, a listener can leave the app with the screen off and hear
 * a chapter's worth of clips without the reader observing a single page turn. Restoring "where the
 * reader was looking" therefore restarts at the top of a page — and on a chapter of several hundred
 * sentences that reads as the beginning of the narration. This type records the fact playback itself
 * knows, so the next open resumes at the passage that was last heard.
 *
 * Expressed in clip indices rather than a text offset because the clip is what an engine can start at
 * exactly: the overlay document is the book's own sentence boundaries, and a reader who resumes at the
 * start of the last sentence has resumed at the last thing they heard.
 *
 * Keyed by book, which is the unit a resume belongs to.
 */
data class SharedMediaOverlayNarrationProgress(
    val bookId: String,
    /** The spine item whose overlay document holds [clipIndex]. */
    val spineItemIndex: Int,
    /**
     * The reader chapter narration was in, or null when the surface had no chapter list.
     *
     * A content document can be split into several reader chapters (a book republished with TOC
     * fragments), so the spine item alone does not say *where* in the document playback was.
     */
    val chapterIndex: Int? = null,
    /** The clip's own index in its overlay document — not an index into the playable plan. */
    val clipIndex: Int,
    val updatedAt: Long = 0L,
)

/**
 * Persists one [SharedMediaOverlayNarrationProgress] per book.
 *
 * Two methods with deliberately different shapes. Reading is a suspend call because a durable store
 * answers off the main thread and the answer is needed before playback can start. Recording is not,
 * because it is called from a player callback on every clip boundary: awaiting a write there would
 * put disk I/O in the audio callback path, and a lost write costs exactly one clip of precision on the
 * next open.
 */
interface SharedMediaOverlayNarrationProgressStore {
    /** The book's last narration position, or null when it has never narrated. */
    suspend fun load(bookId: String): SharedMediaOverlayNarrationProgress?

    /** Records where narration is now. Fire-and-forget; never blocks the caller. */
    fun record(progress: SharedMediaOverlayNarrationProgress)
}

/**
 * The document clip index narration should start at.
 *
 * Saved narration progress is the resume point, because it is the only record of what was actually
 * heard: the text position can be a page behind it, or a whole background session behind it. It is
 * used only when it describes the passage the reader is asking for — same spine item, and the same
 * chapter whenever the surface knows which chapter it is narrating — so jumping to another chapter
 * still narrates that chapter (`RS §9.3.1`).
 *
 * Within that chapter the later of the two wins. Positioning the reader *ahead* of the saved clip is
 * the reader reading on, and they should hear from where they are looking. Positioning them behind it
 * is either a deliberate re-read or a reading position that never caught up with playback (the saved
 * position is page-granular, and a backgrounded reader observes no pages at all); resuming ahead is the
 * only choice that does not rewind a listener who never moved, and a reader who did move back can use
 * skip-previous.
 *
 * Surfaces that know the reader navigated (a TOC tap, a search hit, a chapter change) pass
 * `resumeSavedNarration = false`, which is what keeps "start from where I am looking" working for an
 * explicit move within the chapter narration stopped in.
 *
 * @param saved the book's stored narration position, if any.
 * @param spineItemIndex the spine item the reader is about to narrate.
 * @param chapterIndex the reader chapter that spine item is being narrated as, or null when the
 *   surface has no chapter list.
 * @param readerClipIndex the clip the reader's visible text position resolves to, or null when it
 *   resolves nothing.
 * @param resumeSavedNarration false when the reader's own position must win.
 * @return a **document** clip index, or null to start at the chapter's first playable clip.
 */
fun sharedMediaOverlayStartClipIndex(
    saved: SharedMediaOverlayNarrationProgress?,
    spineItemIndex: Int,
    chapterIndex: Int?,
    readerClipIndex: Int?,
    resumeSavedNarration: Boolean,
): Int? {
    if (!resumeSavedNarration) return readerClipIndex
    val resume = saved
        ?.takeIf { it.spineItemIndex == spineItemIndex && it.clipIndex >= 0 }
        // A split document makes the spine item insufficient: the saved chapter has to be the chapter
        // being narrated, or the resume would jump into another chapter's text. A surface with no
        // chapter list cannot check, and there the stored position is all there is.
        ?.takeIf { progress -> chapterIndex == null || progress.chapterIndex == chapterIndex }
        ?: return readerClipIndex
    return when {
        readerClipIndex == null -> resume.clipIndex
        else -> maxOf(resume.clipIndex, readerClipIndex)
    }
}
