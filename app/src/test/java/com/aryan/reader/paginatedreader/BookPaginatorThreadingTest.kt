package com.aryan.reader.paginatedreader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the main-thread responsiveness contract of chapter processing.
 *
 * ANR traces showed the jsoup/semantic parsing pipeline running inside Choreographer frames
 * when pagination was triggered from UI-dispatcher coroutines. The parse pipeline must be
 * pinned to [kotlinx.coroutines.Dispatchers.Default] inside [BookPaginator.getBlocksForChapter]
 * so the caller's dispatcher cannot make it run on the main thread.
 */
class BookPaginatorThreadingTest {

    @Test
    fun `chapter parse pipeline is pinned to the default dispatcher`() {
        val source = sourceFile("com/aryan/reader/paginatedreader/BookPaginator.kt").readText()
        val getBlocksBody = source.substringAfter("private suspend fun getBlocksForChapter")
            .substringBefore("internal suspend fun getFlowBlocksForChapter")

        assertTrue(
            "getBlocksForChapter must pin its parse work with withContext(Dispatchers.Default)",
            getBlocksBody.contains("withContext(Dispatchers.Default)")
        )
        assertTrue(
            "the jsoup parse must run inside the default-dispatcher block",
            getBlocksBody.indexOf("withContext(Dispatchers.Default)") <
                getBlocksBody.indexOf("Jsoup.parse(htmlToParse, chapter.absPath)")
        )
    }

    @Test
    fun `trace preview truncates before escaping to stay allocation bounded`() {
        // getBlocksForChapter logs a preview of the full chapter HTML. Escaping first copies
        // multi-MB inputs several times for a 220-char preview and OOMs (see
        // docs/crashlytics-triage.md#15); every copy must take(maxLength) before replacing.
        listOf(
            "com/aryan/reader/paginatedreader/BookPaginator.kt",
            "com/aryan/reader/epubreader/ChapterWebView.kt",
            "com/aryan/reader/epubreader/EpubReaderContent.kt"
        ).forEach { path ->
            val body = sourceFile(path).readText()
                .substringAfter("fun String.txtFormatTracePreview")
                .substringBefore("\n}")
            assertTrue(
                "$path must take(maxLength) before the first escaping replace",
                body.indexOf("take(maxLength)") in 0 until body.indexOf(".replace(")
            )
        }
    }

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("app/src/main/java/$relativePath")
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Unable to locate $relativePath from ${File(".").absolutePath}")
    }
}
