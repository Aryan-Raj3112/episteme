package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.SemanticBlock

data class SharedEpubBook(
    val id: String,
    val fileName: String,
    val title: String,
    val author: String? = null,
    val chapters: List<SharedEpubChapter>,
    val css: Map<String, String> = emptyMap()
)

data class SharedEpubChapter(
    val id: String,
    val title: String,
    val plainText: String,
    val semanticBlocks: List<SemanticBlock> = emptyList(),
    val htmlContent: String = "",
    val baseHref: String? = null
)

data class ReaderLocator(
    val chapterIndex: Int = 0,
    val charOffset: Int = 0
)

enum class ReaderReadingMode {
    PAGINATED,
    VERTICAL
}

enum class SharedReaderTextAlign {
    START,
    JUSTIFY,
    CENTER
}

data class ReaderSettings(
    val fontSize: Int = 18,
    val lineSpacing: Float = 1.45f,
    val margin: Int = 48,
    val darkMode: Boolean = false,
    val readingMode: ReaderReadingMode = ReaderReadingMode.PAGINATED,
    val textAlign: SharedReaderTextAlign = SharedReaderTextAlign.START,
    val pageWidth: Int = 760,
    val fontFamily: String = "Default"
)

data class ReaderPage(
    val pageIndex: Int,
    val chapterIndex: Int,
    val chapterTitle: String,
    val text: String,
    val startOffset: Int,
    val endOffset: Int
)

data class PaginatedReaderState(
    val book: SharedEpubBook,
    val pages: List<ReaderPage>,
    val currentPageIndex: Int = 0,
    val settings: ReaderSettings = ReaderSettings()
) {
    val currentPage: ReaderPage? get() = pages.getOrNull(currentPageIndex)
    val progress: Float get() = if (pages.isEmpty()) 0f else ((currentPageIndex + 1).toFloat() / pages.size) * 100f
    val canGoPrevious: Boolean get() = currentPageIndex > 0
    val canGoNext: Boolean get() = currentPageIndex < pages.lastIndex
}

object SampleReaderBooks {
    fun desktopWelcomeBook(): SharedEpubBook {
        return SharedEpubBook(
            id = "desktop_welcome",
            fileName = "Desktop Welcome.epub",
            title = "Episteme Desktop Reader",
            author = "Episteme",
            chapters = listOf(
                SharedEpubChapter(
                    id = "intro",
                    title = "A Careful First Page",
                    plainText = """
                        This is the first desktop paginated reader milestone.

                        It intentionally starts with the quiet parts: page state, chapter navigation, font sizing, margins, light and dark reading surfaces, progress, and a JVM EPUB loader. The Android reader remains where it is, which keeps the mobile app protected while Windows grows its own platform layer.

                        The next pieces can be added one by one: persisted locations, bookmarks, highlights, table of contents polish, keyboard shortcuts, and eventually the richer pagination engine from Android once its platform-specific parts are behind interfaces.
                    """.trimIndent()
                ),
                SharedEpubChapter(
                    id = "scope",
                    title = "What Works Here",
                    plainText = """
                        The desktop shell can import EPUB files and extract readable spine text using the JDK zip APIs. It does not try to render complex CSS, images, MathML, or annotations yet.

                        That limitation is deliberate. A plain paginated reader gives us a working Windows loop without pulling Android WebView, SAF, Room, PDF, or existing reader screens into the first KMP step.
                    """.trimIndent()
                )
            )
        )
    }
}
