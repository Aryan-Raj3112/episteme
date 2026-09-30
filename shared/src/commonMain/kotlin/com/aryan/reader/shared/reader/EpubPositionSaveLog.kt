package com.aryan.reader.shared.reader

import com.aryan.reader.shared.ReaderLocator

/**
 * Common tag for EPUB position-save diagnostics (bridge receipt, autosave
 * emit, close save, persist result, restore result). Ungated on purpose:
 * filter the device console / exported diagnostics for this tag and send the
 * lines when the reader reopens at the wrong place. Keep volume to save
 * milestones plus one line per received bridge position — never chunk text.
 */
const val EpubPositionSaveTag = "EpubPositionSave"

internal fun logEpubPositionSave(message: String) {
    println("[$EpubPositionSaveTag] $message")
    logSharedReaderDiagnostic(EpubPositionSaveTag) { message }
}

internal fun ReaderLocator?.epubPositionSummary(): String {
    if (this == null) return "null"
    return "chapter=${chapterIndex ?: "null"} page=${pageIndex ?: "null"} " +
        "offsets=${startOffset ?: "null"}..${endOffset ?: "null"} " +
        "block=${blockIndex ?: "null"} char=${charOffset ?: "null"} " +
        "cfi=${cfi?.take(120) ?: "null"} quoteChars=${textQuote?.length ?: 0}"
}

/**
 * Restore-guard decision for WebView `readerPositionChanged` reports.
 *
 * A freshly loaded document reports position 0 before the scroll to the
 * restored anchor lands; accepting it clobbers the restored locator and
 * persists chapter-start (observed on iOS). Reports in the restored chapter
 * that fall before the anchor are dropped until the anchor is confirmed.
 * Cross-chapter reports and reports without comparable offsets are always
 * accepted — a real chapter turn must never be swallowed.
 */
internal fun shouldDropPreRestoreBridgePosition(anchor: ReaderLocator?, position: ReaderLocator): Boolean {
    if (anchor == null) return false
    if (anchor.chapterIndex != position.chapterIndex) return false
    val anchorStart = anchor.startOffset ?: return false
    val positionStart = position.startOffset ?: return false
    return positionStart < anchorStart
}
