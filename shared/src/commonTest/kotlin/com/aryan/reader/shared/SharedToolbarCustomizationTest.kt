package com.aryan.reader.shared

import com.aryan.reader.shared.ui.SharedToolbarFlatItem
import com.aryan.reader.shared.ui.SharedToolbarFlatItemType
import com.aryan.reader.shared.ui.SharedToolbarSection
import com.aryan.reader.shared.ui.buildSharedEpubToolbarCommit
import com.aryan.reader.shared.ui.buildSharedEpubToolbarItems
import com.aryan.reader.shared.ui.buildSharedPdfToolbarCommit
import com.aryan.reader.shared.ui.buildSharedPdfToolbarItems
import com.aryan.reader.shared.ui.sanitizeSharedToolbarPlaceholders
import com.aryan.reader.shared.ui.sharedToolbarMoveItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedToolbarCustomizationTest {

    private fun toolItem(section: SharedToolbarSection, id: String): SharedToolbarFlatItem =
        SharedToolbarFlatItem(
            id = "tool_$id",
            type = SharedToolbarFlatItemType.TOOL,
            section = section,
            title = id,
            toolId = id,
        )

    private fun header(section: SharedToolbarSection): SharedToolbarFlatItem =
        SharedToolbarFlatItem(
            id = "header_${section.name}",
            type = SharedToolbarFlatItemType.SECTION_HEADER,
            section = section,
        )

    @Test
    fun `sanitize groups tools under headers and adds empty placeholders`() {
        val input = listOf(
            header(SharedToolbarSection.TOP),
            toolItem(SharedToolbarSection.TOP, "a"),
            header(SharedToolbarSection.BOTTOM),
            header(SharedToolbarSection.HIDDEN),
            SharedToolbarFlatItem("more_header", SharedToolbarFlatItemType.MORE_HEADER),
            SharedToolbarFlatItem("more_x", SharedToolbarFlatItemType.MORE_TOOL, title = "x", toolId = "x"),
        )

        val sanitized = sanitizeSharedToolbarPlaceholders(input)

        assertEquals(SharedToolbarSection.entries.size * 2 + 2, sanitized.size)
        assertEquals("header_TOP", sanitized[0].id)
        assertEquals("tool_a", sanitized[1].id)
        assertEquals(SharedToolbarSection.TOP, sanitized[1].section)
        assertEquals("header_BOTTOM", sanitized[2].id)
        assertEquals("empty_BOTTOM", sanitized[3].id)
        assertEquals(SharedToolbarFlatItemType.EMPTY_PLACEHOLDER, sanitized[3].type)
        assertEquals("header_HIDDEN", sanitized[4].id)
        assertEquals("empty_HIDDEN", sanitized[5].id)
        assertEquals("more_header", sanitized[6].id)
        assertEquals("more_x", sanitized[7].id)
    }

    @Test
    fun `move reassigns section based on destination header`() {
        val items = listOf(
            header(SharedToolbarSection.TOP),
            toolItem(SharedToolbarSection.TOP, "a"),
            toolItem(SharedToolbarSection.TOP, "b"),
            header(SharedToolbarSection.BOTTOM),
            toolItem(SharedToolbarSection.BOTTOM, "c"),
            header(SharedToolbarSection.HIDDEN),
        )

        val moved = sharedToolbarMoveItem(items, "tool_a", "tool_c")

        assertEquals(listOf("tool_b", "tool_c", "tool_a"), moved.filter { it.type == SharedToolbarFlatItemType.TOOL }.map { it.id })
        assertEquals(SharedToolbarSection.BOTTOM, moved.last { it.type == SharedToolbarFlatItemType.TOOL }.section)
    }

    @Test
    fun `move within a section keeps section unchanged`() {
        val items = listOf(
            header(SharedToolbarSection.TOP),
            toolItem(SharedToolbarSection.TOP, "a"),
            toolItem(SharedToolbarSection.TOP, "b"),
            header(SharedToolbarSection.BOTTOM),
        )

        val moved = sharedToolbarMoveItem(items, "tool_a", "tool_b")

        assertEquals(listOf("tool_b", "tool_a"), moved.filter { it.type == SharedToolbarFlatItemType.TOOL }.map { it.id })
        assertTrue(moved.all { it.type != SharedToolbarFlatItemType.TOOL || it.section == SharedToolbarSection.TOP })
    }

    @Test
    fun `move into more menu section is ignored`() {
        val items = listOf(
            header(SharedToolbarSection.TOP),
            toolItem(SharedToolbarSection.TOP, "a"),
            SharedToolbarFlatItem("more_header", SharedToolbarFlatItemType.MORE_HEADER),
            SharedToolbarFlatItem("more_x", SharedToolbarFlatItemType.MORE_TOOL, title = "x", toolId = "x"),
        )

        val moved = sharedToolbarMoveItem(items, "tool_a", "more_x")

        assertEquals(items, moved)
    }

    @Test
    fun `move of non tool items is ignored`() {
        val items = listOf(
            header(SharedToolbarSection.TOP),
            toolItem(SharedToolbarSection.TOP, "a"),
            header(SharedToolbarSection.BOTTOM),
        )

        assertEquals(items, sharedToolbarMoveItem(items, "header_TOP", "tool_a"))
    }

    @Test
    fun `pdf builder maps hidden bottom and more tools to sections`() {
        val preferences = PdfToolbarPreferences(
            hiddenToolIds = setOf(PdfReaderTool.BRIGHTNESS.id),
            toolOrder = listOf(
                PdfReaderTool.THEME,
                PdfReaderTool.SEARCH,
                PdfReaderTool.BRIGHTNESS,
                PdfReaderTool.SLIDER,
                PdfReaderTool.FILE_INFO,
            ),
            bottomToolIds = setOf(PdfReaderTool.SLIDER.id),
        )
        val available = setOf(PdfReaderTool.THEME, PdfReaderTool.SEARCH, PdfReaderTool.BRIGHTNESS, PdfReaderTool.SLIDER, PdfReaderTool.FILE_INFO)

        val items = buildSharedPdfToolbarItems(preferences, available)

        val toolIds = items.filter { it.type == SharedToolbarFlatItemType.TOOL }
        assertEquals(listOf("theme", "search", "slider", "brightness"), toolIds.map { it.toolId })
        assertEquals(SharedToolbarSection.TOP, toolIds[0].section)
        assertEquals(SharedToolbarSection.TOP, toolIds[1].section)
        assertEquals(SharedToolbarSection.BOTTOM, toolIds[2].section)
        assertEquals(SharedToolbarSection.HIDDEN, toolIds[3].section)
        val moreTools = items.filter { it.type == SharedToolbarFlatItemType.MORE_TOOL }
        assertEquals(listOf("file_info"), moreTools.map { it.toolId })
    }

    @Test
    fun `pdf commit derives hidden bottom and order from flat list`() {
        val items = listOf(
            header(SharedToolbarSection.TOP),
            toolItem(SharedToolbarSection.TOP, "theme"),
            toolItem(SharedToolbarSection.BOTTOM, "search"),
            toolItem(SharedToolbarSection.HIDDEN, "brightness"),
            header(SharedToolbarSection.BOTTOM),
            header(SharedToolbarSection.HIDDEN),
            SharedToolbarFlatItem("more_header", SharedToolbarFlatItemType.MORE_HEADER),
            SharedToolbarFlatItem("more_file_info", SharedToolbarFlatItemType.MORE_TOOL, toolId = "file_info"),
        )
        val available = PdfReaderTool.entries.toSet()

        val committed = buildSharedPdfToolbarCommit(items, setOf("slider"), available)

        assertEquals(setOf("brightness"), committed.hiddenToolIds)
        assertEquals(setOf("search"), committed.bottomToolIds)
        assertEquals(available.size, committed.toolOrder.size)
        assertEquals(
            listOf("theme", "search", "brightness", "file_info"),
            committed.toolOrder.take(4).map { it.id },
        )
    }

    @Test
    fun `epub commit mirrors pdf behavior for epub tools`() {
        val toolbarTools = setOf(
            ReaderTool.THEME,
            ReaderTool.SLIDER,
            ReaderTool.SEARCH,
        )
        val items = listOf(
            header(SharedToolbarSection.TOP),
            toolItem(SharedToolbarSection.TOP, "theme"),
            toolItem(SharedToolbarSection.BOTTOM, "search"),
            header(SharedToolbarSection.BOTTOM),
            header(SharedToolbarSection.HIDDEN),
            SharedToolbarFlatItem("more_header", SharedToolbarFlatItemType.MORE_HEADER),
            SharedToolbarFlatItem("more_file_info", SharedToolbarFlatItemType.MORE_TOOL, toolId = "file_info"),
        )

        val committed = buildSharedEpubToolbarCommit(items, setOf("auto_scroll"), toolbarTools)

        assertEquals(setOf("auto_scroll"), committed.hiddenToolIds)
        assertEquals(setOf("search"), committed.bottomToolIds)
        assertEquals(ReaderTool.entries.size, committed.toolOrder.size)
        assertEquals(
            listOf("theme", "search", "file_info"),
            committed.toolOrder.take(3).map { it.id },
        )
    }

    @Test
    fun `epub builder filters to available tools only`() {
        val preferences = ReaderToolbarPreferences(
            toolOrder = listOf(ReaderTool.THEME, ReaderTool.SLIDER, ReaderTool.FILE_INFO, ReaderTool.BOOKMARK),
        )
        val available = setOf(ReaderTool.THEME, ReaderTool.FILE_INFO)

        val items = buildSharedEpubToolbarItems(preferences, setOf(ReaderTool.THEME, ReaderTool.SLIDER), available)

        assertEquals(listOf("theme"), items.filter { it.type == SharedToolbarFlatItemType.TOOL }.map { it.toolId })
        assertEquals(listOf("file_info"), items.filter { it.type == SharedToolbarFlatItemType.MORE_TOOL }.map { it.toolId })
    }
}
