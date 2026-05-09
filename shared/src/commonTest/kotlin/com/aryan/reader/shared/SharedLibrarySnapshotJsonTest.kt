package com.aryan.reader.shared

import androidx.compose.ui.graphics.Color
import com.aryan.reader.shared.reader.ReaderBookmark
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedLibrarySnapshotJsonTest {

    @Test
    fun `snapshot json round trips library records used by desktop persistence`() {
        val tag = Tag(id = "favorite", name = "Favorite", color = 7)
        val snapshot = SharedLibrarySnapshot(
            books = listOf(
                BookItem(
                    id = "book",
                    path = "C:/Books/book.epub",
                    type = FileType.EPUB,
                    displayName = "book.epub",
                    timestamp = 10L,
                    coverImagePath = "C:/Covers/book.png",
                    title = "Book",
                    author = "Ada",
                    progressPercentage = 42f,
                    fileSize = 99L,
                    sourceFolder = "C:/Books",
                    folderTextMetadataParsed = true,
                    seriesName = "Series",
                    seriesIndex = 2.0,
                    tags = listOf(tag),
                    lastPageIndex = 4,
                    readerSettings = ReaderSettings(
                        fontSize = 22,
                        lineSpacing = 1.7f,
                        margin = 64,
                        darkMode = true,
                        readingMode = ReaderReadingMode.VERTICAL,
                        textAlign = SharedReaderTextAlign.JUSTIFY,
                        pageWidth = 840,
                        fontFamily = "Serif",
                        paragraphSpacing = 1.4f,
                        imageScale = 1.2f,
                        horizontalMargin = 40,
                        verticalMargin = 72,
                        themeId = "sepia",
                        textureId = "paper",
                        textureAlpha = 0.35f,
                        customFontPath = "C:/Fonts/custom.ttf",
                        backgroundColorArgb = -328967L,
                        textColorArgb = -12345678L,
                        systemUiMode = SystemUiMode.HIDDEN,
                        pageInfoMode = PageInfoMode.SYNC,
                        pageInfoPosition = PageInfoPosition.TOP,
                        seamlessChapterNavigation = false,
                        chapterTurnDragMultiplier = 1.6f
                    ),
                    readerBookmarks = listOf(
                        ReaderBookmark(
                            id = "book_4",
                            pageIndex = 4,
                            chapterTitle = "Chapter",
                            preview = "A useful paragraph",
                            locator = ReaderLocator(
                                chapterIndex = 0,
                                pageIndex = 4,
                                startOffset = 100,
                                endOffset = 180,
                                textQuote = "A useful paragraph"
                            )
                        )
                    ),
                    readerHighlights = listOf(
                        UserHighlight(
                            id = "highlight_1",
                            cfi = "desktop:0:128:144",
                            text = "useful paragraph",
                            color = HighlightColor.YELLOW,
                            chapterIndex = 0,
                            note = "Remember this",
                            locator = ReaderLocator(
                                chapterIndex = 0,
                                pageIndex = 4,
                                startOffset = 128,
                                endOffset = 144,
                                textQuote = "useful paragraph",
                                cfi = "desktop:0:128:144"
                            )
                        )
                    )
                )
            ),
            shelfRecords = listOf(ShelfRecord(id = "shelf", name = "Shelf", isSmart = true, smartRulesJson = "{}")),
            shelfRefs = listOf(BookShelfRef(bookId = "book", shelfId = "shelf", addedAt = 11L)),
            tags = listOf(tag),
            customFonts = listOf(
                CustomFontItem(
                    id = "font",
                    displayName = "Literata",
                    fileName = "font.ttf",
                    fileExtension = "ttf",
                    path = "C:/Fonts/font.ttf",
                    timestamp = 13L
                )
            ),
            syncedFolders = listOf(SyncedFolder("C:/Books", "Books", lastScanTime = 12L, allowedFileTypes = setOf(FileType.EPUB, FileType.PDF))),
            recentFilesLimit = 20,
            isTabsEnabled = true,
            openTabIds = listOf("book"),
            activeTabBookId = "book",
            pinnedHomeBookIds = setOf("book"),
            pinnedLibraryBookIds = setOf("book"),
            useStrictFileFilter = true,
            appThemeMode = AppThemeMode.DARK,
            appContrastOption = AppContrastOption.HIGH,
            appTextDimFactorLight = 0.75f,
            appTextDimFactorDark = 0.65f,
            appSeedColor = Color(0xFF006C4C),
            customAppThemes = listOf(
                CustomAppTheme(id = "forest", name = "Forest", seedColor = Color(0xFF006C4C))
            ),
            readerToolbarPreferences = ReaderToolbarPreferences(
                hiddenToolIds = setOf(ReaderTool.SEARCH.id),
                toolOrder = listOf(ReaderTool.BOOKMARK, ReaderTool.THEME, ReaderTool.SEARCH),
                bottomToolIds = setOf(ReaderTool.BOOKMARK.id)
            ).sanitized(),
            readerHighlightPalette = ReaderHighlightPalette(
                colors = listOf(HighlightColor.YELLOW, HighlightColor.CYAN)
            ),
            readerTtsReplacementPreferences = ReaderTtsReplacementPreferences(
                globalRules = listOf(
                    ReaderTtsReplacementRule(
                        id = "dr",
                        from = "Dr.",
                        to = "Doctor",
                        wholeWord = false
                    )
                ),
                bookRules = mapOf(
                    "book" to listOf(
                        ReaderTtsReplacementRule(
                            id = "st",
                            from = "St.",
                            to = "Saint",
                            wholeWord = false
                        )
                    )
                ),
                bookSettings = mapOf(
                    "book" to ReaderTtsReplacementBookSettings(disabledGlobalRuleIds = setOf("dr"))
                )
            )
        )

        val decoded = SharedLibrarySnapshotJson.decodeOrEmpty(SharedLibrarySnapshotJson.encode(snapshot))

        assertEquals(snapshot, decoded)
    }

    @Test
    fun `snapshot json tolerates malformed or missing data`() {
        val decoded = SharedLibrarySnapshotJson.decodeOrEmpty("""{"books":[{"id":"missingName"}]}""")

        assertTrue(SharedLibrarySnapshotJson.decodeOrEmpty("not json").books.isEmpty())
        assertTrue(decoded.books.isEmpty())
    }

    @Test
    fun `legacy snapshot hides imported only books from recent home`() {
        val decoded = SharedLibrarySnapshotJson.decodeOrEmpty(
            """
            {
              "schemaVersion": 2,
              "books": [
                {
                  "id": "imported",
                  "path": "C:/Books/imported.epub",
                  "type": "EPUB",
                  "displayName": "imported.epub",
                  "timestamp": 10,
                  "isRecent": true
                },
                {
                  "id": "opened",
                  "path": "C:/Books/opened.epub",
                  "type": "EPUB",
                  "displayName": "opened.epub",
                  "timestamp": 11,
                  "isRecent": true
                }
              ],
              "openTabIds": ["opened"]
            }
            """.trimIndent()
        )

        assertFalse(decoded.books.first { it.id == "imported" }.isRecent)
        assertTrue(decoded.books.first { it.id == "opened" }.isRecent)
    }
}
