package com.aryan.reader.shared

/**
 * Actions exposed from a highlight row's overflow menu.
 *
 * Android presents the palette controls first, followed by note editing and
 * deletion. Keeping that order in shared state makes the mobile readers use
 * the same action contract even though their surrounding drawers differ.
 */
enum class ReaderHighlightListAction {
    CHANGE_COLOR,
    MANAGE_PALETTE,
    EDIT_NOTE,
    DELETE,
}

fun readerHighlightListActions(hasPaletteManager: Boolean): List<ReaderHighlightListAction> = buildList {
    add(ReaderHighlightListAction.CHANGE_COLOR)
    if (hasPaletteManager) add(ReaderHighlightListAction.MANAGE_PALETTE)
    add(ReaderHighlightListAction.EDIT_NOTE)
    add(ReaderHighlightListAction.DELETE)
}
