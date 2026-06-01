package com.aryan.reader.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSerializer
import com.aryan.reader.shared.pdf.SharedPdfBookmark
import com.aryan.reader.shared.pdf.SharedPdfBookmarkSerializer
import com.aryan.reader.shared.pdf.SharedPdfRichDocument
import com.aryan.reader.shared.pdf.SharedPdfRichTextController
import com.aryan.reader.shared.pdf.SharedPdfRichTextLog
import com.aryan.reader.shared.pdf.SharedPdfRichTextSerializer
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
internal fun DesktopPdfAnnotationSidecarEffect(
    documentHandleId: Long,
    annotationFile: File,
    annotations: List<SharedPdfAnnotation>,
    annotationsLoaded: Boolean,
    onAnnotationsLoadedChange: (Boolean) -> Unit,
    onAnnotationsLoaded: (List<SharedPdfAnnotation>) -> Unit,
    onLocalSidecarsChanged: () -> Unit
) {
    LaunchedEffect(documentHandleId) {
        onAnnotationsLoadedChange(false)
        val loadedAnnotations = if (annotationFile.exists()) {
            withContext(Dispatchers.IO) {
                SharedPdfAnnotationSerializer.decode(annotationFile.readText())
            }
        } else {
            emptyList()
        }
        onAnnotationsLoaded(loadedAnnotations)
        logDesktopCloudAnnotations {
            "desktop.local.load_annotations document=$documentHandleId count=${loadedAnnotations.size} " +
                "exists=${annotationFile.exists()} bytes=${annotationFile.length()} ts=${annotationFile.lastModifiedIfFileForCloudLog()}"
        }
        onAnnotationsLoadedChange(true)
    }

    LaunchedEffect(documentHandleId, annotations, annotationsLoaded) {
        if (!annotationsLoaded) return@LaunchedEffect
        val changed = withContext(Dispatchers.IO) {
            runCatching {
                val nextJson = SharedPdfAnnotationSerializer.encode(annotations)
                when {
                    annotations.isEmpty() && annotationFile.isFile -> {
                        annotationFile.delete()
                    }
                    annotations.isEmpty() -> false
                    annotationFile.isFile && annotationFile.readText() == nextJson -> false
                    else -> {
                        annotationFile.parentFile?.mkdirs()
                        annotationFile.writeText(nextJson)
                        true
                    }
                }
            }.getOrDefault(false)
        }
        if (changed) {
            logDesktopCloudAnnotations {
                "desktop.local.save_annotations document=$documentHandleId count=${annotations.size} " +
                    "bytes=${annotationFile.length()} ts=${annotationFile.lastModifiedIfFileForCloudLog()} " +
                    "path=${annotationFile.absolutePath.logPreview(140)}"
            }
            onLocalSidecarsChanged()
        }
    }
}

@Composable
internal fun DesktopPdfBookmarkSidecarEffect(
    documentHandleId: Long,
    bookmarkFile: File,
    bookmarks: List<SharedPdfBookmark>,
    bookmarksLoaded: Boolean,
    onBookmarksLoadedChange: (Boolean) -> Unit,
    onBookmarksLoaded: (List<SharedPdfBookmark>) -> Unit,
    onLocalSidecarsChanged: () -> Unit
) {
    LaunchedEffect(documentHandleId) {
        onBookmarksLoadedChange(false)
        val loadedBookmarks = if (bookmarkFile.exists()) {
            withContext(Dispatchers.IO) {
                SharedPdfBookmarkSerializer.decode(bookmarkFile.readText())
            }
        } else {
            emptyList()
        }
        onBookmarksLoaded(loadedBookmarks)
        onBookmarksLoadedChange(true)
    }

    LaunchedEffect(documentHandleId, bookmarks, bookmarksLoaded) {
        if (!bookmarksLoaded) return@LaunchedEffect
        val changed = withContext(Dispatchers.IO) {
            runCatching {
                val nextJson = SharedPdfBookmarkSerializer.encode(bookmarks)
                when {
                    bookmarks.isEmpty() && !bookmarkFile.isFile -> false
                    bookmarkFile.isFile && bookmarkFile.readText() == nextJson -> false
                    else -> {
                        bookmarkFile.parentFile?.mkdirs()
                        bookmarkFile.writeText(nextJson)
                        true
                    }
                }
            }.getOrDefault(false)
        }
        if (changed) {
            onLocalSidecarsChanged()
        }
    }
}

@Composable
internal fun DesktopPdfRichTextSidecarEffect(
    documentHandleId: Long,
    richTextFile: File,
    richTextController: SharedPdfRichTextController,
    onRichTextLoadedChange: (Boolean) -> Unit
) {
    LaunchedEffect(documentHandleId) {
        onRichTextLoadedChange(false)
        SharedPdfRichTextLog.d(
            "desktop.loadRichText start path=\"${richTextFile.absolutePath.logPreview(160)}\" exists=${richTextFile.exists()}"
        )
        val loadedRichText = withContext(Dispatchers.IO) {
            if (richTextFile.exists()) {
                val raw = richTextFile.readText()
                SharedPdfRichTextLog.d(
                    "desktop.loadRichText read path=\"${richTextFile.absolutePath.logPreview(160)}\" rawLen=${raw.length}"
                )
                SharedPdfRichTextSerializer.decode(raw)
            } else {
                SharedPdfRichDocument()
            }
        }
        SharedPdfRichTextLog.d(
            "desktop.loadRichText decoded textLen=${loadedRichText.text.length} spans=${loadedRichText.spans.size}"
        )
        richTextController.replaceDocument(loadedRichText)
        onRichTextLoadedChange(true)
        SharedPdfRichTextLog.d("desktop.loadRichText ready")
    }
}

@Composable
internal fun DesktopPdfSearchIndexSidecarEffect(
    documentHandleId: Long,
    document: DesktopPdfDocument,
    searchIndexFile: File,
    onIndexedSearchPageCountChange: (Int) -> Unit,
    onSearchIndexingChange: (Boolean) -> Unit
) {
    LaunchedEffect(documentHandleId) {
        val restoredPageCount = withContext(Dispatchers.IO) {
            restoreDesktopPdfSearchIndex(document, searchIndexFile)
        }
        onIndexedSearchPageCountChange(restoredPageCount)
        onSearchIndexingChange(restoredPageCount < document.pageCount)
        logPdfZoomPerf {
            "search_index_restore indexed=$restoredPageCount/${document.pageCount} " +
                "active=${restoredPageCount < document.pageCount}"
        }
        withContext(Dispatchers.IO) {
            DesktopPdfium.indexSearchPages(
                document = document,
                onProgress = { indexed, _ ->
                    onIndexedSearchPageCountChange(indexed)
                    logPdfZoomPerf { "search_index_progress indexed=$indexed/${document.pageCount}" }
                },
                shouldContinue = { isActive }
            )
            if (isActive) {
                saveDesktopPdfSearchIndex(document, searchIndexFile)
            }
        }
        if (!isActive) return@LaunchedEffect
        val indexedPageCount = document.indexedSearchTextPageCount()
        onIndexedSearchPageCountChange(indexedPageCount)
        onSearchIndexingChange(false)
        logPdfZoomPerf { "search_index_done indexed=$indexedPageCount/${document.pageCount}" }
    }
}

@Composable
internal fun DesktopPdfSearchResultsEffect(
    documentHandleId: Long,
    document: DesktopPdfDocument,
    searchQuery: String,
    indexedSearchPageCount: Int,
    onSearchResultsChange: (List<SharedPdfSearchResult>) -> Unit
) {
    LaunchedEffect(documentHandleId, searchQuery, indexedSearchPageCount) {
        val normalizedQuery = searchQuery.trim()
        val results = if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                DesktopPdfium.search(document, normalizedQuery)
            }
        }
        onSearchResultsChange(results)
    }
}

private fun File.lastModifiedIfFileForCloudLog(): Long {
    return if (isFile) lastModified() else 0L
}
