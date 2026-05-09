package com.aryan.reader.shared

private val DefaultReaderBottomToolIds: Set<String>
    get() = setOf(
        ReaderTool.SLIDER.id,
        ReaderTool.TOC.id,
        ReaderTool.FORMAT.id,
        ReaderTool.SEARCH.id,
        ReaderTool.AI_FEATURES.id,
        ReaderTool.TTS_CONTROLS.id
    )

enum class ReaderTool(
    val id: String,
    val title: String,
    val category: String,
    val supportsDesktopQuickAction: Boolean = false
) {
    DICTIONARY("dictionary", "External Apps", "Top Bar", supportsDesktopQuickAction = true),
    THEME("theme", "Theme Settings", "Top Bar", supportsDesktopQuickAction = true),
    SLIDER("slider", "Navigation Slider", "Bottom Bar"),
    TOC("toc", "Sidebar", "Bottom Bar"),
    FORMAT("format", "Text Formatting", "Bottom Bar"),
    SEARCH("search", "Search", "Bottom Bar", supportsDesktopQuickAction = true),
    AI_FEATURES("ai_features", "AI Features", "Bottom Bar", supportsDesktopQuickAction = true),
    TTS_CONTROLS("tts_controls", "TTS Controls", "Bottom Bar", supportsDesktopQuickAction = true),
    READING_MODE("reading_mode", "Reading Mode", "Overflow Menu"),
    BOOKMARK("bookmark", "Bookmark", "Overflow Menu", supportsDesktopQuickAction = true),
    TAP_TO_TURN("tap_to_turn", "Tap to Turn Pages", "Overflow Menu"),
    VOLUME_SCROLL("volume_scroll", "Volume Button Scrolling", "Overflow Menu"),
    PAGE_TURN_ANIM("page_turn_anim", "Realistic Page Turns", "Overflow Menu"),
    KEEP_SCREEN_ON("keep_screen_on", "Keep Screen On", "Overflow Menu"),
    VISUAL_OPTIONS("visual_options", "Visual Options", "Overflow Menu"),
    AUTO_SCROLL("auto_scroll", "Auto Scroll", "Overflow Menu", supportsDesktopQuickAction = true),
    TTS_SETTINGS("tts_settings", "TTS Voice Settings", "Overflow Menu"),
    TTS_REPLACEMENTS("tts_replacements", "TTS Word Replacements", "Overflow Menu");

    companion object {
        fun fromId(id: String): ReaderTool? {
            return entries.firstOrNull { it.id == id || it.name == id }
        }
    }
}

data class ReaderToolbarPreferences(
    val hiddenToolIds: Set<String> = emptySet(),
    val toolOrder: List<ReaderTool> = ReaderTool.entries.toList(),
    val bottomToolIds: Set<String> = DefaultReaderBottomToolIds
) {
    fun sanitized(): ReaderToolbarPreferences {
        val orderedTools = (toolOrder + ReaderTool.entries.toList())
            .distinct()
            .filter { it in ReaderTool.entries }
        val knownToolIds = ReaderTool.entries.mapTo(mutableSetOf()) { it.id }
        return copy(
            hiddenToolIds = hiddenToolIds.filterTo(mutableSetOf()) { it in knownToolIds },
            toolOrder = orderedTools,
            bottomToolIds = bottomToolIds.filterTo(mutableSetOf()) { it in knownToolIds }
        )
    }

    fun isVisible(tool: ReaderTool): Boolean {
        return tool.id !in hiddenToolIds
    }

    fun isBottom(tool: ReaderTool): Boolean {
        return tool.id in bottomToolIds
    }

    fun withVisibility(tool: ReaderTool, hidden: Boolean): ReaderToolbarPreferences {
        val nextHidden = if (hidden) hiddenToolIds + tool.id else hiddenToolIds - tool.id
        return copy(hiddenToolIds = nextHidden).sanitized()
    }

    fun withBottomPlacement(tool: ReaderTool, bottom: Boolean): ReaderToolbarPreferences {
        val nextBottom = if (bottom) bottomToolIds + tool.id else bottomToolIds - tool.id
        return copy(bottomToolIds = nextBottom).sanitized()
    }

    fun withToolOrder(order: List<ReaderTool>): ReaderToolbarPreferences {
        return copy(toolOrder = order).sanitized()
    }

    fun orderedVisibleTools(): List<ReaderTool> {
        return sanitized().toolOrder.filter(::isVisible)
    }

    companion object {
        val defaultBottomToolIds: Set<String> get() = DefaultReaderBottomToolIds
    }
}
