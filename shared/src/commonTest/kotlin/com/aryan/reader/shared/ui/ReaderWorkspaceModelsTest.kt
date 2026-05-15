package com.aryan.reader.shared.ui

import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReaderAutoScrollState
import com.aryan.reader.shared.ReaderCloudTtsState
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderWorkspaceModelsTest {

    @Test
    fun `epub workspace maps shared toolbar preferences without toolbar tab`() {
        val session = ReaderEngine().createSession(readerFixtureBook())
        val preferences = ReaderToolbarPreferences(
            hiddenToolIds = setOf(ReaderTool.THEME.id, ReaderTool.FORMAT.id, ReaderTool.BOOKMARK.id),
            bottomToolIds = setOf(ReaderTool.SLIDER.id, ReaderTool.SEARCH.id)
        )

        val model = epubReaderWorkspaceModel(
            session = session,
            toolbarPreferences = preferences,
            extrasState = ReaderExtrasState(),
            aiAvailable = true
        )

        assertEquals(ReaderWorkspaceKind.EPUB, model.kind)
        assertEquals(
            listOf(
                ReaderWorkspaceLeftSection.CONTENTS,
                ReaderWorkspaceLeftSection.NOTES,
                ReaderWorkspaceLeftSection.BOOKMARKS
            ),
            model.leftSections
        )
        assertFalse(ReaderWorkspaceLeftSection.SEARCH in model.leftSections)
        assertFalse(ReaderWorkspaceTopAction.BOOKMARK in model.topActions)
        assertFalse(ReaderWorkspaceInspectorSection.APPEARANCE in model.inspectorSections)
        assertTrue(ReaderWorkspaceInspectorSection.AI_TTS in model.inspectorSections)
        assertFalse(ReaderWorkspaceInspectorSection.TOOLBAR in model.inspectorSections)
        assertTrue(ReaderWorkspaceTopAction.SEARCH in model.topActions)
        assertTrue(ReaderWorkspaceTopAction.FULL_SCREEN in model.topActions)
        assertTrue(ReaderWorkspaceTopAction.AI in model.topActions)
        assertTrue(ReaderWorkspaceBottomAction.PAGE_SLIDER in model.bottomActions)
        assertFalse(model.panelDefaults.leftOpen)
        assertFalse(model.panelDefaults.inspectorOpen)
        assertFalse(model.chrome.preferAutoHide)
    }

    @Test
    fun `chrome model is forced visible for active reader states`() {
        val model = readerWorkspaceChromeModel(
            preferAutoHide = false,
            searchActive = true,
            leftPanelOpen = false,
            inspectorOpen = true,
            annotationEditing = true,
            richTextEditing = true,
            loading = true,
            errorMessage = "Failed",
            autoScroll = ReaderAutoScrollState(enabled = true),
            ttsBusy = true
        )

        assertFalse(model.preferAutoHide)
        assertTrue(model.forceVisible)
        assertEquals(
            setOf("search", "inspector", "annotation", "rich-text", "loading", "error", "auto-scroll", "tts"),
            model.forceVisibleReasons
        )
    }

    @Test
    fun `epub workspace ignores desktop visual options in inspector`() {
        val session = ReaderEngine().createSession(readerFixtureBook())
        val preferences = ReaderToolbarPreferences(
            hiddenToolIds = ReaderTool.entries
                .filterNot { it == ReaderTool.VISUAL_OPTIONS }
                .mapTo(mutableSetOf()) { it.id }
        )

        val model = epubReaderWorkspaceModel(
            session = session,
            toolbarPreferences = preferences,
            extrasState = ReaderExtrasState(),
            aiAvailable = true
        )

        assertFalse(ReaderWorkspaceInspectorSection.TOOLS in model.inspectorSections)
        assertFalse(ReaderWorkspaceTopAction.TOOLS in model.topActions)
    }

    @Test
    fun `epub workspace ignores external lookup in inspector`() {
        val session = ReaderEngine().createSession(readerFixtureBook())
        val preferences = ReaderToolbarPreferences(
            hiddenToolIds = ReaderTool.entries
                .filterNot { it == ReaderTool.DICTIONARY }
                .mapTo(mutableSetOf()) { it.id }
        )

        val model = epubReaderWorkspaceModel(
            session = session,
            toolbarPreferences = preferences,
            extrasState = ReaderExtrasState(),
            aiAvailable = false,
            cloudTtsAvailable = false,
            externalLookupAvailable = true
        )

        assertFalse(ReaderWorkspaceInspectorSection.AI_TTS in model.inspectorSections)
        assertFalse(ReaderWorkspaceTopAction.TOOLS in model.topActions)
    }

    @Test
    fun `toolbar quick actions preserve visibility order and bottom placement`() {
        val preferences = ReaderToolbarPreferences(
            hiddenToolIds = setOf(ReaderTool.BOOKMARK.id),
            toolOrder = listOf(
                ReaderTool.AUTO_SCROLL,
                ReaderTool.SEARCH,
                ReaderTool.AI_FEATURES,
                ReaderTool.THEME,
                ReaderTool.BOOKMARK
            ) + ReaderTool.entries,
            bottomToolIds = setOf(ReaderTool.SEARCH.id, ReaderTool.AI_FEATURES.id)
        )

        val topTools = readerWorkspaceQuickActionTools(
            toolbarPreferences = preferences,
            bottom = false,
            aiAvailable = true
        )
        val bottomToolsWithoutAi = readerWorkspaceQuickActionTools(
            toolbarPreferences = preferences,
            bottom = true,
            aiAvailable = false
        )
        val bottomToolsWithAi = readerWorkspaceQuickActionTools(
            toolbarPreferences = preferences,
            bottom = true,
            aiAvailable = true
        )

        assertEquals(listOf(ReaderTool.AUTO_SCROLL, ReaderTool.THEME), topTools.take(2))
        assertEquals(listOf(ReaderTool.SEARCH), bottomToolsWithoutAi)
        assertEquals(listOf(ReaderTool.SEARCH, ReaderTool.AI_FEATURES), bottomToolsWithAi)
        assertFalse(ReaderTool.BOOKMARK in topTools)
        assertFalse(ReaderTool.BOOKMARK in bottomToolsWithAi)
    }

    @Test
    fun `toolbar quick actions hide online tools when unavailable`() {
        val preferences = ReaderToolbarPreferences(
            toolOrder = listOf(
                ReaderTool.DICTIONARY,
                ReaderTool.SEARCH,
                ReaderTool.AI_FEATURES,
                ReaderTool.TTS_CONTROLS
            ) + ReaderTool.entries,
            bottomToolIds = setOf(
                ReaderTool.DICTIONARY.id,
                ReaderTool.SEARCH.id,
                ReaderTool.AI_FEATURES.id,
                ReaderTool.TTS_CONTROLS.id
            )
        )

        val tools = readerWorkspaceQuickActionTools(
            toolbarPreferences = preferences,
            bottom = true,
            aiAvailable = false,
            cloudTtsAvailable = false,
            externalLookupAvailable = false
        )

        assertEquals(listOf(ReaderTool.SEARCH), tools)
    }

    @Test
    fun `pdf workspace defaults to reading first while keeping annotation tools in inspector`() {
        val model = pdfReaderWorkspaceModel(
            state = SharedPdfReaderState.initial(pageCount = 4),
            displayMode = PdfDisplayMode.PAGINATION,
            hasContents = true,
            hasBookmarks = true,
            hasAnnotations = true,
            hasEmbeddedComments = true,
            searchActive = false,
            annotationEditing = false,
            richTextEditing = false,
            loading = false,
            errorMessage = null,
            extrasState = ReaderExtrasState(),
            aiAvailable = true
        )

        assertEquals(ReaderWorkspaceKind.PDF, model.kind)
        assertNull(model.defaultPdfInteractionMode)
        assertEquals(
            listOf(
                ReaderWorkspaceLeftSection.CONTENTS,
                ReaderWorkspaceLeftSection.NOTES,
                ReaderWorkspaceLeftSection.BOOKMARKS,
                ReaderWorkspaceLeftSection.PAGES
            ),
            model.leftSections
        )
        assertFalse(ReaderWorkspaceLeftSection.SEARCH in model.leftSections)
        assertTrue(ReaderWorkspaceInspectorSection.APPEARANCE in model.inspectorSections)
        assertTrue(ReaderWorkspaceInspectorSection.TOOLS in model.inspectorSections)
        assertTrue(ReaderWorkspaceInspectorSection.AI_TTS in model.inspectorSections)
        assertTrue(ReaderWorkspaceInspectorSection.TOOLBAR in model.inspectorSections)
        assertTrue(ReaderWorkspaceTopAction.BOOKMARK in model.topActions)
        assertTrue(ReaderWorkspaceTopAction.FULL_SCREEN in model.topActions)
        assertTrue(ReaderWorkspaceTopAction.AI in model.topActions)
        assertFalse(model.panelDefaults.leftOpen)
        assertFalse(model.panelDefaults.inspectorOpen)
        assertFalse(model.chrome.preferAutoHide)
    }

    @Test
    fun `pdf workspace forces chrome for search editing errors tts and vertical auto scroll`() {
        val model = pdfReaderWorkspaceModel(
            state = SharedPdfReaderState.initial(pageCount = 4).copy(searchQuery = "needle"),
            displayMode = PdfDisplayMode.VERTICAL_SCROLL,
            hasContents = false,
            hasBookmarks = false,
            hasAnnotations = false,
            hasEmbeddedComments = false,
            searchActive = false,
            annotationEditing = true,
            richTextEditing = false,
            loading = false,
            errorMessage = "Problem",
            extrasState = ReaderExtrasState(
                autoScroll = ReaderAutoScrollState(enabled = true),
                cloudTts = ReaderCloudTtsState(isPlaying = true)
            ),
            aiAvailable = false
        )

        assertTrue(model.chrome.forceVisible)
        assertFalse(model.chrome.preferAutoHide)
        assertTrue("search" in model.chrome.forceVisibleReasons)
        assertTrue("annotation" in model.chrome.forceVisibleReasons)
        assertTrue("error" in model.chrome.forceVisibleReasons)
        assertTrue("auto-scroll" in model.chrome.forceVisibleReasons)
        assertTrue("tts" in model.chrome.forceVisibleReasons)
        assertFalse(ReaderWorkspaceTopAction.AI in model.topActions)
    }

    private fun readerFixtureBook(): SharedEpubBook {
        return SharedEpubBook(
            id = "reader_fixture",
            fileName = "Reader Fixture.epub",
            title = "Reader Fixture",
            chapters = listOf(
                SharedEpubChapter(
                    id = "intro",
                    title = "Intro",
                    plainText = "A short reader fixture for workspace model tests."
                )
            )
        )
    }
}
