package com.aryan.reader.shared

enum class EpubOverflowMenuSection {
    CUSTOMIZE_TOOLBAR,
    HIDDEN_TOOLS,
    VIEW_ORIGINAL_PDF,
    DELETE_TEXT_VIEW,
    READING_MODE,
    BOOKMARK,
    TAP_TO_TURN,
    VOLUME_SCROLL,
    PAGE_TURN_ANIM,
    KEEP_SCREEN_ON,
    VISUAL_OPTIONS,
    AUTO_SCROLL,
    BOOK_REPLACEMENTS,
    TTS_SETTINGS,
    FILE_INFO
}

fun epubOverflowMenuSections(
    hiddenTools: Set<String>,
    hasHiddenToolbarTools: Boolean,
    hasToggleReflow: Boolean,
    hasDeleteReflow: Boolean,
    hasFileInfo: Boolean = true,
): List<EpubOverflowMenuSection> = buildList {
    add(EpubOverflowMenuSection.CUSTOMIZE_TOOLBAR)
    if (hasHiddenToolbarTools) add(EpubOverflowMenuSection.HIDDEN_TOOLS)
    if (hasToggleReflow) add(EpubOverflowMenuSection.VIEW_ORIGINAL_PDF)
    if (hasDeleteReflow) add(EpubOverflowMenuSection.DELETE_TEXT_VIEW)
    if (ReaderTool.READING_MODE.name !in hiddenTools) add(EpubOverflowMenuSection.READING_MODE)
    if (ReaderTool.BOOKMARK.name !in hiddenTools) add(EpubOverflowMenuSection.BOOKMARK)
    if (ReaderTool.TAP_TO_TURN.name !in hiddenTools) add(EpubOverflowMenuSection.TAP_TO_TURN)
    if (ReaderTool.VOLUME_SCROLL.name !in hiddenTools) add(EpubOverflowMenuSection.VOLUME_SCROLL)
    if (ReaderTool.PAGE_TURN_ANIM.name !in hiddenTools) add(EpubOverflowMenuSection.PAGE_TURN_ANIM)
    if (ReaderTool.KEEP_SCREEN_ON.name !in hiddenTools) add(EpubOverflowMenuSection.KEEP_SCREEN_ON)
    if (ReaderTool.VISUAL_OPTIONS.name !in hiddenTools) add(EpubOverflowMenuSection.VISUAL_OPTIONS)
    if (ReaderTool.AUTO_SCROLL.name !in hiddenTools) add(EpubOverflowMenuSection.AUTO_SCROLL)
    if (ReaderTool.BOOK_REPLACEMENTS.name !in hiddenTools) add(EpubOverflowMenuSection.BOOK_REPLACEMENTS)
    if (ReaderTool.TTS_SETTINGS.name !in hiddenTools || ReaderTool.TTS_REPLACEMENTS.name !in hiddenTools) {
        add(EpubOverflowMenuSection.TTS_SETTINGS)
    }
    if (hasFileInfo && ReaderTool.FILE_INFO.name !in hiddenTools) add(EpubOverflowMenuSection.FILE_INFO)
}

enum class PdfOverflowMenuSection {
    CUSTOMIZE_TOOLBAR,
    HIDDEN_TOOLS,
    OCR_LANGUAGE,
    VISUAL_OPTIONS,
    READING_MODE,
    TAP_TO_TURN,
    KEEP_SCREEN_ON,
    AUTO_SCROLL,
    TTS_SETTINGS,
    BOOKMARK,
    PAGE_MANAGEMENT,
    REFLOW,
    FILE_ACTIONS,
    FILE_INFO
}

fun pdfOverflowMenuSections(
    hiddenTools: Set<String>,
    hasHiddenToolbarTools: Boolean,
    isPro: Boolean,
    effectiveFileType: FileType,
    hasFileInfo: Boolean = true,
    canPrintDocument: Boolean = true,
): List<PdfOverflowMenuSection> = buildList {
    add(PdfOverflowMenuSection.CUSTOMIZE_TOOLBAR)
    if (hasHiddenToolbarTools) add(PdfOverflowMenuSection.HIDDEN_TOOLS)
    if (isPro && PdfReaderTool.OCR_LANGUAGE.name !in hiddenTools) add(PdfOverflowMenuSection.OCR_LANGUAGE)
    if (PdfReaderTool.VISUAL_OPTIONS.name !in hiddenTools) add(PdfOverflowMenuSection.VISUAL_OPTIONS)
    if (PdfReaderTool.READING_MODE.name !in hiddenTools) add(PdfOverflowMenuSection.READING_MODE)
    if (PdfReaderTool.TAP_TO_TURN.name !in hiddenTools) add(PdfOverflowMenuSection.TAP_TO_TURN)
    if (PdfReaderTool.KEEP_SCREEN_ON.name !in hiddenTools) add(PdfOverflowMenuSection.KEEP_SCREEN_ON)
    if (PdfReaderTool.AUTO_SCROLL.name !in hiddenTools) add(PdfOverflowMenuSection.AUTO_SCROLL)
    if (PdfReaderTool.TTS_SETTINGS.name !in hiddenTools || PdfReaderTool.TTS_REPLACEMENTS.name !in hiddenTools) {
        add(PdfOverflowMenuSection.TTS_SETTINGS)
    }
    if (PdfReaderTool.BOOKMARK.name !in hiddenTools) add(PdfOverflowMenuSection.BOOKMARK)
    if (PdfReaderTool.PAGE_MANAGEMENT.name !in hiddenTools) add(PdfOverflowMenuSection.PAGE_MANAGEMENT)
    if (PdfReaderTool.REFLOW.name !in hiddenTools) add(PdfOverflowMenuSection.REFLOW)
    if (
        PdfReaderTool.SHARE.name !in hiddenTools ||
        (effectiveFileType == FileType.PDF && PdfReaderTool.SAVE_COPY.name !in hiddenTools) ||
        (effectiveFileType == FileType.PDF && canPrintDocument && PdfReaderTool.PRINT.name !in hiddenTools)
    ) {
        add(PdfOverflowMenuSection.FILE_ACTIONS)
    }
    if (hasFileInfo && PdfReaderTool.FILE_INFO.name !in hiddenTools) add(PdfOverflowMenuSection.FILE_INFO)
}
