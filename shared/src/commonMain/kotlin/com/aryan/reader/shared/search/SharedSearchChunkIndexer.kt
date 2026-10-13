package com.aryan.reader.shared.search

import com.aryan.reader.paginatedreader.MATH_PLACEHOLDER_CHAR
import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.SemanticFlexContainer
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.paginatedreader.SemanticWrappingBlock

/**
 * Turns the shapes platforms already extract into [SharedSearchChunk]s.
 * Pure shared mapping so every shell indexes identical text; platforms only
 * fetch the source (EPUB blocks, PDF native pages, office/plain text).
 * Never fed OCR output.
 */
object SharedSearchChunkIndexer {

    /**
     * Flattens one chapter's semantic blocks in document order. Text blocks
     * keep their block/cfi/offset identity so hits deep-link; images
     * contribute only non-blank alt text; math and spacers are skipped.
     */
    fun fromSemanticBlocks(chapterIndex: Int, blocks: List<SemanticBlock>): List<SharedSearchChunk> {
        val out = mutableListOf<SharedSearchChunk>()
        fun emit(
            blockIndex: Int,
            charOffset: Int?,
            cfi: String?,
            anchorId: String?,
            text: String,
        ) {
            val clean = text.replace(MATH_PLACEHOLDER_CHAR, "").trim()
            if (clean.isBlank()) return
            out += SharedGlobalSearchPolicy.chunkTextForIndex(
                chapterIndex = chapterIndex,
                blockIndex = blockIndex,
                charOffset = charOffset,
                cfi = cfi,
                anchorId = anchorId,
                text = clean,
                baseChunkIndex = out.size,
            )
        }
        fun walk(block: SemanticBlock) {
            when (block) {
                is SemanticTextBlock -> emit(
                    blockIndex = block.blockIndex,
                    charOffset = block.startCharOffsetInSource,
                    cfi = block.cfi,
                    anchorId = block.elementId,
                    text = block.text,
                )
                is SemanticList -> block.items.forEach(::walk)
                is SemanticTable -> block.rows.forEach { row ->
                    row.forEach { cell -> cell.content.forEach(::walk) }
                }
                is SemanticFlexContainer -> block.children.forEach(::walk)
                is SemanticWrappingBlock -> block.paragraphsToWrap.forEach(::walk)
                is SemanticImage -> {
                    if (!block.altText.isNullOrBlank()) {
                        emit(
                            blockIndex = block.blockIndex,
                            charOffset = null,
                            cfi = block.cfi,
                            anchorId = block.elementId,
                            text = block.altText,
                        )
                    }
                }
                else -> Unit // Math, spacers: no indexable prose.
            }
        }
        blocks.forEach(::walk)
        return out
    }

    /** One entry per PDF page (native text only); oversized pages split. */
    fun fromPdfPageTexts(pages: List<String>): List<SharedSearchChunk> {
        val out = mutableListOf<SharedSearchChunk>()
        pages.forEachIndexed { pageIndex, text ->
            if (text.isBlank()) return@forEachIndexed
            out += SharedGlobalSearchPolicy.chunkTextForIndex(
                pageIndex = pageIndex,
                text = text.trim(),
                baseChunkIndex = out.size,
            )
        }
        return out
    }

    /** Single-document text (TXT/MD/HTML/FB2/office plain output). */
    fun fromPlainText(text: String, chapterIndex: Int? = null): List<SharedSearchChunk> {
        if (text.isBlank()) return emptyList()
        return SharedGlobalSearchPolicy.chunkTextForIndex(
            chapterIndex = chapterIndex,
            text = text.trim(),
        )
    }

    /** Slide decks: slide order is the chapter axis. */
    fun fromSlideTexts(slides: List<String>): List<SharedSearchChunk> {
        val out = mutableListOf<SharedSearchChunk>()
        slides.forEachIndexed { slideIndex, text ->
            if (text.isBlank()) return@forEachIndexed
            out += SharedGlobalSearchPolicy.chunkTextForIndex(
                chapterIndex = slideIndex,
                text = text.trim(),
                baseChunkIndex = out.size,
            )
        }
        return out
    }
}
