package com.aryan.reader.shared

enum class PdfReaderTool(val id: String, val title: String, val category: String) {
    DICTIONARY("dictionary", "External Apps", "Top Bar"),
    THEME("theme", "Theme", "Top Bar"),
    BRIGHTNESS("brightness", "Brightness", "Top Bar"),
    LOCK_PANNING("lock_panning", "Lock Panning", "Top Bar"),
    FILE_INFO("file_info", "File Information", "Overflow Menu"),
    VISUAL_OPTIONS("visual_options", "Visual Options", "Overflow Menu"),
    TAP_TO_TURN("tap_to_turn", "Tap to Turn Pages", "Overflow Menu"),
    SLIDER("slider", "Navigation Slider", "Bottom Bar"),
    TOC("toc", "Sidebar", "Bottom Bar"),
    SEARCH("search", "Search", "Bottom Bar"),
    HIGHLIGHT_ALL("highlight_all", "Highlight Selectable Text", "Bottom Bar"),
    AI_FEATURES("ai_features", "AI Features", "Bottom Bar"),
    EDIT_MODE("edit_mode", "Edit Mode", "Bottom Bar"),
    TTS_CONTROLS("tts_controls", "TTS Controls", "Bottom Bar"),
    OCR_LANGUAGE("ocr_language", "OCR Language", "Overflow Menu"),
    READING_MODE("reading_mode", "Reading Mode", "Overflow Menu"),
    KEEP_SCREEN_ON("keep_screen_on", "Keep Screen On", "Overflow Menu"),
    SCREEN_ORIENTATION("screen_orientation", "Screen Orientation", "Top Bar"),
    AUTO_SCROLL("auto_scroll", "Auto Scroll", "Overflow Menu"),
    TTS_SETTINGS("tts_settings", "TTS Voice Settings", "Overflow Menu"),
    TTS_REPLACEMENTS("tts_replacements", "TTS Word Replacements", "Overflow Menu"),
    BOOKMARK("bookmark", "Bookmark", "Overflow Menu"),
    PAGE_MANAGEMENT("page_management", "Page Management", "Overflow Menu"),
    REFLOW("reflow", "Text View / Reflow", "Overflow Menu"),
    SHARE("share", "Share", "Overflow Menu"),
    SAVE_COPY("save_copy", "Save Copy to Device", "Overflow Menu"),
    PRINT("print", "Print", "Overflow Menu");

    val supportsToolbarPlacement: Boolean
        get() = this in toolbarPlacementTools

    companion object {
        private val toolbarPlacementTools = setOf(
            DICTIONARY, THEME, BRIGHTNESS, LOCK_PANNING, SLIDER, TOC, SEARCH,
            HIGHLIGHT_ALL, AI_FEATURES, EDIT_MODE, TTS_CONTROLS, SCREEN_ORIENTATION,
        )

        fun fromId(value: String): PdfReaderTool? =
            entries.firstOrNull { it.id == value || it.name == value }
    }
}

fun isPdfReaderToolEnabledDuringTts(
    tool: PdfReaderTool,
    isTtsPlayingOrLoading: Boolean,
): Boolean = !isTtsPlayingOrLoading || tool !in setOf(
    PdfReaderTool.SLIDER,
    PdfReaderTool.TOC,
    PdfReaderTool.SEARCH,
)

data class PdfToolbarPreferences(
    val hiddenToolIds: Set<String> = setOf(
        PdfReaderTool.SCREEN_ORIENTATION.id,
        PdfReaderTool.HIGHLIGHT_ALL.id,
        PdfReaderTool.BRIGHTNESS.id,
    ),
    val toolOrder: List<PdfReaderTool> = PdfReaderTool.entries.toList(),
    val bottomToolIds: Set<String> = PdfReaderTool.entries
        .filter { it.category == "Bottom Bar" }
        .mapTo(mutableSetOf()) { it.id },
) {
    fun sanitized(availableTools: Set<PdfReaderTool> = PdfReaderTool.entries.toSet()): PdfToolbarPreferences {
        val ordered = (toolOrder + PdfReaderTool.entries)
            .distinct()
            .filter { it in availableTools }
        val ids = availableTools.mapTo(mutableSetOf()) { it.id }
        return copy(
            hiddenToolIds = hiddenToolIds.filterTo(mutableSetOf()) { it in ids },
            toolOrder = ordered,
            bottomToolIds = bottomToolIds.filterTo(mutableSetOf()) { id ->
                PdfReaderTool.fromId(id)?.let { it in availableTools && it.supportsToolbarPlacement } == true
            },
        )
    }

    fun isVisible(tool: PdfReaderTool): Boolean = tool.id !in hiddenToolIds
    fun isBottom(tool: PdfReaderTool): Boolean = tool.id in bottomToolIds
    fun orderedVisibleTools(): List<PdfReaderTool> = sanitized().toolOrder.filter(::isVisible)

    fun withVisibility(tool: PdfReaderTool, hidden: Boolean): PdfToolbarPreferences =
        copy(hiddenToolIds = if (hidden) hiddenToolIds + tool.id else hiddenToolIds - tool.id).sanitized()

    fun withBottomPlacement(tool: PdfReaderTool, bottom: Boolean): PdfToolbarPreferences =
        copy(bottomToolIds = if (bottom) bottomToolIds + tool.id else bottomToolIds - tool.id).sanitized()

    fun move(tool: PdfReaderTool, delta: Int): PdfToolbarPreferences {
        val order = sanitized().toolOrder.toMutableList()
        val from = order.indexOf(tool)
        if (from < 0) return this
        val to = (from + delta).coerceIn(order.indices)
        if (to == from) return this
        order.removeAt(from)
        order.add(to, tool)
        return copy(toolOrder = order).sanitized()
    }

    fun moveWithinAvailable(
        tool: PdfReaderTool,
        delta: Int,
        availableTools: Set<PdfReaderTool>,
    ): PdfToolbarPreferences {
        val fullOrder = sanitized().toolOrder
        val availableOrder = fullOrder.filter { it in availableTools }.toMutableList()
        val from = availableOrder.indexOf(tool)
        if (from < 0) return this
        val to = (from + delta).coerceIn(availableOrder.indices)
        if (to == from) return this
        availableOrder.removeAt(from)
        availableOrder.add(to, tool)
        val iterator = availableOrder.iterator()
        return copy(
            toolOrder = fullOrder.map { current ->
                if (current in availableTools) iterator.next() else current
            },
        ).sanitized()
    }
}
