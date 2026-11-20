package com.aryan.reader.shared.ui

/** Lines the annotation editor's note field shows, with or without a keyboard. */
const val SharedMobileAnnotationSheetNoteMaxLines: Int = 5

/**
 * What an open keyboard is allowed to take away from an annotation editor.
 *
 * Reported against both the EPUB and the PDF editors: focusing the note field collapsed the editor
 * to the field alone — the styling rows, the quoted snippet and the tools disappeared and the field
 * grew past its resting height until it covered the whole sheet. The intent is narrower: only the
 * Delete/Save row steps aside. It sits outside the editor's content, the draft is committed as it is
 * typed (`SharedReaderNoteAutosave`) and again on dismissal, so nothing becomes unreachable and the
 * keyboard never has to be closed to reach anything.
 *
 * This is a policy rather than three inline conditionals so the contract is pinned by a test: the
 * keyboard takes the action row, and nothing else.
 */
data class SharedMobileAnnotationSheetLayout(
    /** The styling/colour rows, the snippet and the tool row; they keep their resting place. */
    val editorChromeVisible: Boolean,
    /** Lines the note field shows; the keyboard must not grow it over the rest of the editor. */
    val noteFieldMaxLines: Int,
    /** Delete/Save, the only row an open keyboard takes away. */
    val bottomActionsVisible: Boolean,
)

fun sharedMobileAnnotationSheetLayout(isImeVisible: Boolean): SharedMobileAnnotationSheetLayout =
    SharedMobileAnnotationSheetLayout(
        editorChromeVisible = true,
        noteFieldMaxLines = SharedMobileAnnotationSheetNoteMaxLines,
        bottomActionsVisible = !isImeVisible,
    )
