package com.aryan.reader.paginatedreader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aryan.reader.shared.UserHighlight
import timber.log.Timber

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
    chapterBlocksKey: Any?,
    chapterBlocks: suspend (Int) -> List<TextContentBlock>?,
    onHighlightsRepaired: (List<UserHighlight>) -> Unit,
): ChapterHighlightIndexes {
    val indexes = remember { ChapterHighlightIndexes() }
    LaunchedEffect(highlights, chapterBlocksKey) {
        for (chapterIndex in highlights.mapNotNull { it.locator.chapterIndex ?: it.chapterIndex }.distinct()) {
            if (indexes.byChapterIndex.containsKey(chapterIndex)) continue

            // Blocks come from a paginator that may not exist yet in this reading mode, and parsing a
            // chapter can fail on its own. Neither is a verdict on the chapter, so neither is cached.
            val blocks = runCatching { chapterBlocks(chapterIndex) }.getOrNull()
            if (blocks.isNullOrEmpty()) {
                Timber.tag(TAG_CHAPTER_INDEX_DIAG).d(
                    "chapter_index_unavailable chapter=$chapterIndex indexed=0 " +
                        "hint=blocks_not_ready_or_paginator_absent"
                )
                continue
            }

            val index = EpubChapterTextIndex.of(chapterIndex, blocks.toSemanticTextBlocks())
            if (index == null) {
                Timber.tag(TAG_CHAPTER_INDEX_DIAG).d(
                    "chapter_index_unusable chapter=$chapterIndex blocks=${blocks.size} indexed=0"
                )
                continue
            }
            // Published as one immutable map so every composed page recomposes when it lands. Pages
            // composed before this finished read no index and would otherwise keep showing nothing.
            indexes.index(chapterIndex, index)
            Timber.tag(TAG_CHAPTER_INDEX_DIAG).d(
                "chapter_index chapter=$chapterIndex blocks=${blocks.size} indexed=1"
            )

            // The chapter's text is available now, so repair what is already stored. Locators written
            // before the coordinate space was fixed hold offsets pointing somewhere else entirely, and
            // repairing them here is the only chance to fix them: a reader only ever sees the chapters
            // it opens, and only while they are open. Without a write-back the same search runs again
            // on every open, and the wrong offsets survive in the database.
            val repaired = index.repairHighlights(highlights)
            if (repaired.unchanged) continue
            Timber.tag(TAG_CHAPTER_INDEX_DIAG).d(
                "highlight_repair chapter=$chapterIndex repaired=${repaired.repaired} of=${highlights.size}"
            )
            onHighlightsRepaired(repaired.highlights)
        }
    }
    return indexes
}

internal const val TAG_CHAPTER_INDEX_DIAG = "HighlightDiag"