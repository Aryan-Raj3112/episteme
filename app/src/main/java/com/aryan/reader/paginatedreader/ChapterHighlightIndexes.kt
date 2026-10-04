package com.aryan.reader.paginatedreader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import com.aryan.reader.epubreader.logHighlightTrace
import com.aryan.reader.shared.UserHighlight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The chapter text indexes the native surfaces place highlights against, one per chapter that holds a
 * highlight.
 *
 * Built once for the whole reader rather than per surface: the paginated and native-vertical surfaces
 * used to each keep their own copy, so a highlight repaired by one was not repaired for the other, and
 * switching modes repeated the work. One holder means a highlight created in the WebView is anchored
 * once and every surface places the same result.
 */
@Stable
class ChapterHighlightIndexes {

    /** Only chapters that actually produced an index appear here. See [rememberChapterHighlightIndexes]. */
    var byChapterIndex: Map<Int, EpubChapterTextIndex> by mutableStateOf(emptyMap())
        private set

    fun forChapter(chapterIndex: Int?): EpubChapterTextIndex? = chapterIndex?.let { byChapterIndex[it] }

    internal fun index(chapterIndex: Int, index: EpubChapterTextIndex) {
        byChapterIndex = byChapterIndex + (chapterIndex to index)
    }
}

/**
 * Builds and keeps [ChapterHighlightIndexes] for the chapters that hold highlights, repairing the
 * locators that need it.
 *
 * A highlight created in a WebView surface carries only its selected text, so placing it anywhere else
 * means resolving that text against the whole chapter rather than one page's blocks. The chapter's
 * blocks may still need parsing, so this runs off the composition and the pages read the result.
 *
 * Two things here are load-bearing, and both were wrong before:
 *
 * - **A chapter whose blocks cannot be read yet is not remembered as unindexed.** Blocks become
 *   available when the paginator is created, which can be after this first runs. Caching the failure
 *   left the chapter unindexed for the rest of the session, so every later pass skipped it and the
 *   highlight stayed invisible in every mode — the same symptom as a genuinely unresolvable quote,
 *   with nothing in the log to tell them apart. [chapterBlocksKey] exists to re-run this pass when
 *   the source of blocks changes.
 * - **Every chapter holding a highlight is indexed, not only the ones that look unanchored.** A
 *   highlight written before the coordinate space was fixed *has* offsets, and they are wrong, which
 *   is exactly what the repair exists for.
 */
@Composable
fun rememberChapterHighlightIndexes(
    highlights: List<UserHighlight>,
    chapterBlocks: suspend (Int) -> List<TextContentBlock>?,
    onHighlightsRepaired: (List<UserHighlight>) -> Unit,
): ChapterHighlightIndexes {
    val indexes = remember { ChapterHighlightIndexes() }
    // Read through these so the long-lived reconciliation below always sees the current paginator and
    // callback without being restarted for them. See the note on why it must not be restarted.
    val currentBlocks by rememberUpdatedState(chapterBlocks)
    val currentHighlights by rememberUpdatedState(highlights)
    val currentOnRepaired by rememberUpdatedState(onHighlightsRepaired)

    /*
     * Watches the chapters that hold highlights and reconciles them, forever, for as long as the reader
     * is open.
     *
     * Three earlier shapes all failed on a real book, and each failure was the same lesson: this work
     * has to survive the reader's own churn.
     *
     * A `LaunchedEffect` keyed on the highlight list was cancelled by every change of identity — a real
     * book rebuilds its paginator as layout settles — and each cancellation threw away the wait for the
     * chapter's blocks, so the index silently never arrived. Keying on the highlights' settled shape
     * instead fixed that but introduced the next problem: the reader hands highlights over from
     * preferences to the database, and during that handover the list is briefly empty. The pass then ran
     * with no chapters, finished, and when the highlights came back the key was unchanged so nothing
     * woke it. The highlight stayed unanchored for the rest of the session.
     *
     * Collecting a snapshot flow fixes both. It does not care about identity, it cannot be cancelled by
     * an unrelated recomposition, and an empty emission simply waits for the next one instead of
     * finishing the pass.
     */
    DisposableEffect(Unit) {
        onDispose { logHighlightTrace("chapter_index_effect disposed=1") }
    }

    LaunchedEffect(Unit) {
        try {
            snapshotFlow {
                highlights.mapNotNull { it.locator.chapterIndex ?: it.chapterIndex }.distinct().sorted()
            }.collect { chapters ->
                for (chapterIndex in chapters) {
                    val existing = indexes.byChapterIndex[chapterIndex]
                    if (existing != null) {
                        // A chapter that is already indexed still has to repair its highlights. The index is
                        // built once, but highlights arrive one at a time — including while the reader is
                        // open — and a highlight added after the build has never been through the resolver.
                        repair(currentHighlights, existing, chapterIndex, currentOnRepaired)
                        continue
                    }

                    // Wait for the blocks rather than deciding they are unavailable. See [awaitChapterBlocks].
                    val blocks = awaitChapterBlocks(chapterIndex, currentBlocks) ?: continue

                    val index = EpubChapterTextIndex.of(chapterIndex, blocks.toSemanticTextBlocks())
                    if (index == null) {
                        logHighlightTrace(
                            "chapter_index_unusable chapter=$chapterIndex blocks=${blocks.size} indexed=0"
                        )
                        continue
                    }
                    // Published as one immutable map so every composed page recomposes when it lands. Pages
                    // composed before this finished read no index and would otherwise keep showing nothing.
                    indexes.index(chapterIndex, index)
                    logHighlightTrace("chapter_index chapter=$chapterIndex blocks=${blocks.size} indexed=1")
                    repair(currentHighlights, index, chapterIndex, currentOnRepaired)
                }
            }
        } catch (t: Throwable) {
            logHighlightTrace("chapter_index_effect ended=${t::class.simpleName}")
            // Cancellation is how a composition announces it is done, so it is reported and then passed
            // on rather than swallowed: swallowing it here would hide the one failure a reader of these
            // logs most needs to see, which is the work being torn down before it finished.
            if (t is CancellationException) throw t
        }
    }
    return indexes
}

/**
 * Rewrites the locators in [highlights] that this chapter can place, and hands the corrected list back.
 *
 * Only the chapter's own text can say where a stored locator should have pointed, and this is the only
 * chance to say it: a reader only ever sees the chapters it opens, and only while they are open. The
 * correction has to be written back or the same wrong offsets are trusted again on the next open.
 */
private fun repair(
    highlights: List<UserHighlight>,
    index: EpubChapterTextIndex,
    chapterIndex: Int,
    onHighlightsRepaired: (List<UserHighlight>) -> Unit
) {
    val repaired = index.repairHighlights(highlights)
    if (repaired.unchanged) return
    logHighlightTrace(
        "highlight_repair chapter=$chapterIndex repaired=${repaired.repaired} of=${highlights.size}"
    )
    /*
     * Report the CFI before and after, because that field is what a WebView surface places by and a
     * correction that silently leaves it alone is invisible in every other line: the highlight then looks
     * repaired — it has an offset and a block — while the surface that reads the CFI is still being handed
     * the position the highlight was created with, in a coordinate space it does not share.
     */
    val before = highlights.associateBy { it.id }
    repaired.highlights.forEach { after ->
        val was = before[after.id] ?: return@forEach
        if (was.locator.cfi != after.locator.cfi) {
            logHighlightTrace(
                "repair_cfi id=${after.id} was=${was.locator.cfi} now=${after.locator.cfi} " +
                    "action=" + if (after.locator.cfi == null) "dropped" else "replaced"
            )
        }
    }
    onHighlightsRepaired(repaired.highlights)
}

/**
 * Waits for a chapter's text blocks, or gives up and says so.
 *
 * Blocks come from the paginator, which does not exist in WebView mode and is created part-way through
 * entering a paginated one. Whether they are available is therefore a *readiness* question, and it
 * changes over the life of a single composition without anything the effect is keyed on changing: the
 * same paginator object simply starts answering. Asking once and treating "not yet" as "never" is what
 * left a highlight made in the WebView invisible in every other mode for the rest of the session, so
 * this waits instead.
 *
 * Bounded, because a chapter that genuinely cannot be read — a bad index, a book that has gone away —
 * would otherwise wait forever and hold the pass open. The wait is also cancelled with the enclosing
 * effect, so a pass made stale by new highlights or a new paginator stops rather than reviving.
 */
private suspend fun awaitChapterBlocks(
    chapterIndex: Int,
    chapterBlocks: suspend (Int) -> List<TextContentBlock>?
): List<TextContentBlock>? {
    val startedAt = TimeSource.Monotonic.markNow()
    var attempts = 0
    while (currentCoroutineContext().isActive) {
        val blocks = runCatching { chapterBlocks(chapterIndex) }.getOrNull()
        if (!blocks.isNullOrEmpty()) return blocks
        attempts++
        if (startedAt.elapsedNow() < CHAPTER_BLOCKS_TIMEOUT) {
            logHighlightTrace("chapter_index_wait chapter=$chapterIndex attempt=$attempts not_ready=1")
            delay(CHAPTER_BLOCKS_RETRY_MS)
            continue
        }
        logHighlightTrace(
            "chapter_index_gave_up chapter=$chapterIndex attempts=$attempts indexed=0 " +
                "hint=paginator_absent_or_chapter_unreadable"
        )
        return null
    }
    logHighlightTrace("chapter_index_wait chapter=$chapterIndex aborted=1 attempts=$attempts")
    return null
}

/** How long to keep asking for a chapter's blocks before accepting that this chapter cannot be read. */
private val CHAPTER_BLOCKS_TIMEOUT = 30.seconds

private const val CHAPTER_BLOCKS_RETRY_MS = 250L